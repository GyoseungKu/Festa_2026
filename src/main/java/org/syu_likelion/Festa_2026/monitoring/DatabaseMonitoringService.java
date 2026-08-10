package org.syu_likelion.Festa_2026.monitoring;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DatabaseMonitoringService {
    private static final String STATUS_QUERY = """
            SHOW GLOBAL STATUS WHERE Variable_name IN
            ('Threads_connected','Threads_running','Questions','Slow_queries','Uptime',
             'Innodb_buffer_pool_read_requests','Innodb_buffer_pool_reads',
             'Innodb_row_lock_waits','Innodb_deadlocks','Com_commit','Com_rollback')
            """;
    private final DataSource dataSource;
    private final MonitoringProperties properties;
    private final Clock clock;
    private volatile DatabaseSnapshot current = DatabaseSnapshot.starting();
    private volatile Counters previous;

    public DatabaseMonitoringService(DataSource dataSource, MonitoringProperties properties, Clock clock) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void initialProbe() { probe(); }

    @Scheduled(fixedDelayString = "${monitoring.db-probe-interval-ms:15000}")
    void scheduledProbe() { probe(); }

    public DatabaseSnapshot snapshot() { return current; }

    void probe() {
        Instant sampledAt = Instant.now(clock);
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(properties.dbQueryTimeoutSeconds());
            try (ResultSet ignored = statement.executeQuery("SELECT 1")) {
                if (!ignored.next()) throw new SQLException("Health query returned no row");
            }
            long latencyMs = elapsedMs(started);
            try {
                Map<String, Long> status = status(statement);
                long maxConnections = variable(statement, "max_connections");
                current = detailedSnapshot(status, maxConnections, latencyMs, sampledAt);
            } catch (SQLException insufficientPermission) {
                current = new DatabaseSnapshot("UP_LIMITED", sampledAt, latencyMs, false,
                        null, null, null, null, null, null, null, null, null, null, null);
            }
        } catch (SQLException unavailable) {
            current = new DatabaseSnapshot("DOWN", sampledAt, elapsedMs(started), false,
                    null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    private Map<String, Long> status(Statement statement) throws SQLException {
        Map<String, Long> values = new HashMap<>();
        try (ResultSet rows = statement.executeQuery(STATUS_QUERY)) {
            while (rows.next()) values.put(rows.getString(1), parse(rows.getString(2)));
        }
        return values;
    }

    private long variable(Statement statement, String name) throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SHOW GLOBAL VARIABLES WHERE Variable_name = '" + name + "'")) {
            return rows.next() ? parse(rows.getString(2)) : 0;
        }
    }

    private DatabaseSnapshot detailedSnapshot(Map<String, Long> values, long maxConnections,
                                              long latencyMs, Instant sampledAt) {
        long questions = value(values, "Questions");
        long slowQueries = value(values, "Slow_queries");
        long deadlocks = value(values, "Innodb_deadlocks");
        Counters old = previous;
        double qps = 0;
        long newSlow = 0, newDeadlocks = 0;
        if (old != null) {
            double seconds = Math.max(1, (sampledAt.toEpochMilli() - old.sampledAt().toEpochMilli()) / 1000.0);
            qps = Math.max(0, questions - old.questions()) / seconds;
            newSlow = Math.max(0, slowQueries - old.slowQueries());
            newDeadlocks = Math.max(0, deadlocks - old.deadlocks());
        }
        previous = new Counters(sampledAt, questions, slowQueries, deadlocks);
        long reads = value(values, "Innodb_buffer_pool_reads");
        long readRequests = value(values, "Innodb_buffer_pool_read_requests");
        Double hitRate = readRequests == 0 ? null : round((1 - (double) reads / readRequests) * 100);
        return new DatabaseSnapshot("UP", sampledAt, latencyMs, true,
                value(values, "Threads_connected"), value(values, "Threads_running"), maxConnections,
                round(qps), newSlow, newDeadlocks, value(values, "Innodb_row_lock_waits"),
                hitRate, value(values, "Com_commit"), value(values, "Com_rollback"), value(values, "Uptime"));
    }

    private long value(Map<String, Long> values, String name) { return values.getOrDefault(name, 0L); }
    private long parse(String value) { try { return Long.parseLong(value); } catch (RuntimeException ignored) { return 0; } }
    private long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    private record Counters(Instant sampledAt, long questions, long slowQueries, long deadlocks) { }

    public record DatabaseSnapshot(String status, Instant sampledAt, long latencyMs,
                                   boolean detailedMetricsAvailable, Long connections,
                                   Long runningThreads, Long maxConnections, Double queriesPerSecond,
                                   Long newSlowQueries, Long newDeadlocks, Long rowLockWaits,
                                   Double bufferPoolHitRatePercent, Long commits, Long rollbacks,
                                   Long uptimeSeconds) {
        static DatabaseSnapshot starting() {
            return new DatabaseSnapshot("STARTING", null, 0, false,
                    null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
