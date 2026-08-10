package org.syu_likelion.Festa_2026.logging;

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
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AsyncApiRequestLogWriter {
    private static final Logger log = LoggerFactory.getLogger(AsyncApiRequestLogWriter.class);
    private static final String INSERT_SQL = """
            INSERT INTO api_request_logs
            (request_id, user_uuid, client_ip, http_method, request_path, route_pattern,
             response_status, duration_ms, host, scheme, user_agent, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final ApiRequestLogProperties properties;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ArrayBlockingQueue<ApiRequestLogRecord> queue;
    private final LongAdder dropped = new LongAdder();
    private final AtomicLong lastWarningNanos = new AtomicLong();
    private volatile boolean running;
    private Thread worker;
    private long nextCleanupNanos;

    public AsyncApiRequestLogWriter(ApiRequestLogProperties properties, JdbcTemplate jdbc, Clock clock) {
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
        worker = new Thread(this::workLoop, "api-request-log-writer");
        worker.setDaemon(true);
        worker.start();
    }

    public void publish(ApiRequestLogRecord record) {
        if (!properties.enabled() || record == null) return;
        if (!queue.offer(record)) {
            dropped.increment();
            warnRateLimited("queue_full");
        }
    }

    public int queueSize() { return queue.size(); }

    public int queueCapacity() { return properties.queueCapacity(); }

    public long droppedTotal() { return dropped.sum(); }

    private void workLoop() {
        List<ApiRequestLogRecord> batch = new ArrayList<>(properties.batchSize());
        while (running || !queue.isEmpty()) {
            try {
                ApiRequestLogRecord first = queue.poll(properties.flushInterval().toMillis(), TimeUnit.MILLISECONDS);
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

    private void write(List<ApiRequestLogRecord> batch) {
        jdbc.batchUpdate(INSERT_SQL, batch, batch.size(), this::bind);
    }

    private void bind(PreparedStatement statement, ApiRequestLogRecord record) throws java.sql.SQLException {
        statement.setString(1, record.requestId());
        if (record.userUuid() == null) statement.setNull(2, Types.BINARY);
        else statement.setBytes(2, uuidBytes(record.userUuid()));
        statement.setString(3, record.clientIp());
        statement.setString(4, record.httpMethod());
        statement.setString(5, record.requestPath());
        if (record.routePattern() == null) statement.setNull(6, Types.VARCHAR);
        else statement.setString(6, record.routePattern());
        statement.setInt(7, record.responseStatus());
        statement.setLong(8, record.durationMs());
        statement.setString(9, record.host());
        statement.setString(10, record.scheme());
        if (record.userAgent() == null) statement.setNull(11, Types.VARCHAR);
        else statement.setString(11, record.userAgent());
        statement.setTimestamp(12, Timestamp.from(record.occurredAt()));
    }

    private byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    private void requeue(List<ApiRequestLogRecord> batch) {
        for (ApiRequestLogRecord record : batch) {
            if (!queue.offer(record)) dropped.increment();
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
        for (int i = 0; i < properties.cleanupMaxBatches(); i++) {
            int deleted = jdbc.update("DELETE FROM api_request_logs WHERE occurred_at < ? LIMIT ?",
                    cutoff, properties.cleanupBatchSize());
            if (deleted < properties.cleanupBatchSize()) break;
        }
    }

    private void warnRateLimited(String reason) {
        long now = System.nanoTime();
        long previous = lastWarningNanos.get();
        if (now - previous >= WARNING_INTERVAL_NANOS && lastWarningNanos.compareAndSet(previous, now)) {
            log.warn("API request log loss reason={} droppedTotal={} queueSize={} success=false",
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
