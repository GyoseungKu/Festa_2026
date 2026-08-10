package org.syu_likelion.Festa_2026.analytics;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AsyncFrontendEventWriter {
    private static final Logger log = LoggerFactory.getLogger(AsyncFrontendEventWriter.class);
    private static final String INSERT_SQL = """
            INSERT INTO frontend_event_logs
            (event_id, request_id, user_uuid, session_id, event_type, route, target_id,
             duration_ms, client_occurred_at, received_at, app_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE event_id = event_id
            """;
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final FrontendAnalyticsProperties properties;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ArrayBlockingQueue<FrontendEventRecord> queue;
    private final LongAdder dropped = new LongAdder();
    private final AtomicLong lastWarningNanos = new AtomicLong();
    private volatile boolean running;
    private Thread worker;
    private long nextCleanupNanos;

    public AsyncFrontendEventWriter(FrontendAnalyticsProperties properties,
                                    JdbcTemplate jdbc, Clock clock) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
        this.queue = new ArrayBlockingQueue<>(properties.queueCapacity());
    }

    @PostConstruct
    void start() {
        if (!properties.enabled()) return;
        running = true;
        nextCleanupNanos = System.nanoTime() + properties.cleanupInterval().toNanos();
        worker = new Thread(this::workLoop, "frontend-event-log-writer");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean publish(FrontendEventRecord record) {
        if (!properties.enabled() || record == null) return false;
        boolean accepted = queue.offer(record);
        if (!accepted) {
            dropped.increment();
            warnRateLimited("queue_full");
        }
        return accepted;
    }

    private void workLoop() {
        List<FrontendEventRecord> batch = new ArrayList<>(properties.batchSize());
        while (running || !queue.isEmpty()) {
            try {
                FrontendEventRecord first = queue.poll(
                        properties.flushInterval().toMillis(), TimeUnit.MILLISECONDS);
                if (first != null) batch.add(first);
                queue.drainTo(batch, properties.batchSize() - batch.size());
                if (!batch.isEmpty()) {
                    write(List.copyOf(batch));
                    batch.clear();
                }
                cleanupIfDue();
            } catch (InterruptedException interrupted) {
                if (running) Thread.currentThread().interrupt();
            } catch (RuntimeException databaseFailure) {
                if (running) requeue(batch);
                else dropped.add(batch.size() + queue.size());
                batch.clear();
                if (!running) queue.clear();
                warnRateLimited("database_unavailable");
                if (running) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(databaseBackoffMs()));
            }
        }
    }

    private void write(List<FrontendEventRecord> batch) {
        jdbc.batchUpdate(INSERT_SQL, batch, batch.size(), this::bind);
    }

    private void bind(PreparedStatement statement, FrontendEventRecord event) throws java.sql.SQLException {
        statement.setBytes(1, uuidBytes(event.eventId()));
        statement.setString(2, event.requestId());
        if (event.userUuid() == null) statement.setNull(3, Types.BINARY);
        else statement.setBytes(3, uuidBytes(event.userUuid()));
        statement.setBytes(4, uuidBytes(event.sessionId()));
        statement.setString(5, event.eventType().name());
        statement.setString(6, event.route());
        if (event.targetId() == null) statement.setNull(7, Types.VARCHAR);
        else statement.setString(7, event.targetId());
        if (event.durationMs() == null) statement.setNull(8, Types.BIGINT);
        else statement.setLong(8, event.durationMs());
        statement.setTimestamp(9, Timestamp.from(event.clientOccurredAt()));
        statement.setTimestamp(10, Timestamp.from(event.receivedAt()));
        if (event.appVersion() == null) statement.setNull(11, Types.VARCHAR);
        else statement.setString(11, event.appVersion());
    }

    private byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    private void requeue(List<FrontendEventRecord> batch) {
        for (FrontendEventRecord event : batch) {
            if (!queue.offer(event)) dropped.increment();
        }
    }

    private long databaseBackoffMs() {
        return Math.max(100, Math.min(1_000, properties.flushInterval().toMillis()));
    }

    private void cleanupIfDue() {
        long now = System.nanoTime();
        if (now < nextCleanupNanos) return;
        nextCleanupNanos = now + properties.cleanupInterval().toNanos();
        Timestamp cutoff = Timestamp.from(Instant.now(clock).minus(properties.retention()));
        for (int index = 0; index < properties.cleanupMaxBatches(); index++) {
            int deleted = jdbc.update("DELETE FROM frontend_event_logs WHERE received_at < ? LIMIT ?",
                    cutoff, properties.cleanupBatchSize());
            if (deleted < properties.cleanupBatchSize()) break;
        }
    }

    private void warnRateLimited(String reason) {
        long now = System.nanoTime();
        long previous = lastWarningNanos.get();
        if (now - previous >= WARNING_INTERVAL_NANOS && lastWarningNanos.compareAndSet(previous, now)) {
            log.warn("Frontend event log loss reason={} droppedTotal={} queueSize={} success=false",
                    reason, dropped.sum(), queue.size());
        }
    }

    @PreDestroy
    void stop() {
        if (worker == null) return;
        running = false;
        worker.interrupt();
        try {
            worker.join(3_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
