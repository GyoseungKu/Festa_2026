package org.syu_likelion.Festa_2026.monitoring;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.analytics.AsyncFrontendEventWriter;
import org.syu_likelion.Festa_2026.logging.AsyncApiRequestLogWriter;
import org.syu_likelion.Festa_2026.monitoring.DatabaseMonitoringService.DatabaseSnapshot;
import org.syu_likelion.Festa_2026.monitoring.HttpLiveMetrics.HttpSnapshot;
import org.syu_likelion.Festa_2026.monitoring.PresenceTracker.PresenceSnapshot;

@Service
public class SystemMonitoringService {
    private final MeterRegistry meters;
    private final PresenceTracker presence;
    private final HttpLiveMetrics http;
    private final DatabaseMonitoringService database;
    private final AsyncApiRequestLogWriter requestLogs;
    private final AsyncFrontendEventWriter frontendEvents;
    private final Clock clock;

    public SystemMonitoringService(MeterRegistry meters, PresenceTracker presence,
                                   HttpLiveMetrics http, DatabaseMonitoringService database,
                                   AsyncApiRequestLogWriter requestLogs,
                                   AsyncFrontendEventWriter frontendEvents, Clock clock) {
        this.meters = meters;
        this.presence = presence;
        this.http = http;
        this.database = database;
        this.requestLogs = requestLogs;
        this.frontendEvents = frontendEvents;
        this.clock = clock;
    }

    public SystemSnapshot snapshot() {
        PresenceSnapshot presenceSnapshot = presence.snapshot();
        HttpSnapshot httpSnapshot = http.snapshot();
        DatabaseSnapshot databaseSnapshot = database.snapshot();
        RuntimeSnapshot runtime = runtime();
        ConnectionPoolSnapshot pool = pool();
        QueueSnapshot queues = new QueueSnapshot(
                requestLogs.queueSize(), requestLogs.queueCapacity(), requestLogs.droppedTotal(),
                frontendEvents.queueSize(), frontendEvents.queueCapacity(), frontendEvents.droppedTotal());
        return new SystemSnapshot(Instant.now(clock), overall(runtime, pool, databaseSnapshot, queues),
                presenceSnapshot, httpSnapshot, runtime, pool, databaseSnapshot, queues);
    }

    private RuntimeSnapshot runtime() {
        Long heapUsed = rounded(metric("jvm.memory.used", "area", "heap"));
        Long heapMax = rounded(metric("jvm.memory.max", "area", "heap"));
        return new RuntimeSnapshot(percent(metric("process.cpu.usage")),
                percent(metric("system.cpu.usage")), metric("system.load.average.1m"),
                heapUsed, heapMax, percentage(heapUsed, heapMax),
                rounded(metric("jvm.threads.live")), rounded(metric("jvm.threads.peak")),
                rounded(metric("disk.free")), rounded(metric("disk.total")),
                rounded(metric("process.uptime")));
    }

    private ConnectionPoolSnapshot pool() {
        return new ConnectionPoolSnapshot(rounded(metric("hikaricp.connections.active")),
                rounded(metric("hikaricp.connections.idle")),
                rounded(metric("hikaricp.connections.pending")),
                rounded(metric("hikaricp.connections.max")),
                rounded(metric("hikaricp.connections.min")));
    }

    private String overall(RuntimeSnapshot runtime, ConnectionPoolSnapshot pool,
                           DatabaseSnapshot database, QueueSnapshot queues) {
        if ("DOWN".equals(database.status())) return "CRITICAL";
        if (greater(runtime.processCpuPercent(), 90) || greater(runtime.heapUsagePercent(), 92)
                || greater(pool.pending(), 0) || queues.apiRequestDroppedTotal() > 0
                || queues.frontendEventDroppedTotal() > 0) return "WARNING";
        if ("STARTING".equals(database.status()) || "UP_LIMITED".equals(database.status())) return "LIMITED";
        return "HEALTHY";
    }

    private Double metric(String name, String... tags) {
        double total = 0;
        boolean found = false;
        var search = meters.find(name);
        if (tags.length > 0) search.tags(tags);
        for (Meter meter : search.meters()) {
            for (var measurement : meter.measure()) {
                if (Double.isFinite(measurement.getValue())) {
                    total += measurement.getValue();
                    found = true;
                }
            }
        }
        return found ? round(total) : null;
    }

    private Double percent(Double ratio) { return ratio == null ? null : round(ratio * 100); }
    private Double percentage(Long used, Long max) {
        return used == null || max == null || max <= 0 ? null : round((double) used * 100 / max);
    }
    private Long rounded(Double value) { return value == null ? null : Math.round(value); }
    private boolean greater(Number value, double threshold) { return value != null && value.doubleValue() > threshold; }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    public record RuntimeSnapshot(Double processCpuPercent, Double systemCpuPercent,
                                  Double systemLoadAverage, Long heapUsedBytes, Long heapMaxBytes,
                                  Double heapUsagePercent, Long liveThreads, Long peakThreads,
                                  Long diskFreeBytes, Long diskTotalBytes, Long uptimeSeconds) { }

    public record ConnectionPoolSnapshot(Long active, Long idle, Long pending, Long max, Long min) { }

    public record QueueSnapshot(int apiRequestQueueSize, int apiRequestQueueCapacity,
                                long apiRequestDroppedTotal, int frontendEventQueueSize,
                                int frontendEventQueueCapacity, long frontendEventDroppedTotal) { }

    public record SystemSnapshot(Instant sampledAt, String status, PresenceSnapshot presence,
                                 HttpSnapshot http, RuntimeSnapshot runtime,
                                 ConnectionPoolSnapshot connectionPool, DatabaseSnapshot database,
                                 QueueSnapshot queues) { }
}
