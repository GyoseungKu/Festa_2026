package org.syu_likelion.Festa_2026.monitoring;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class HttpLiveMetrics {
    private static final int WINDOW_SECONDS = 60;
    private static final long[] LATENCY_BOUNDS_MS = {50, 100, 250, 500, 1_000, 2_000, 5_000, Long.MAX_VALUE};
    private final Bucket[] buckets = new Bucket[WINDOW_SECONDS];
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Clock clock;

    public HttpLiveMetrics(Clock clock) {
        this.clock = clock;
        for (int index = 0; index < buckets.length; index++) buckets[index] = new Bucket();
    }

    public void requestStarted() { inFlight.incrementAndGet(); }

    public void requestFinished(int status, long durationMs) {
        inFlight.updateAndGet(value -> Math.max(0, value - 1));
        long epochSecond = clock.instant().getEpochSecond();
        Bucket bucket = buckets[Math.floorMod(epochSecond, WINDOW_SECONDS)];
        synchronized (bucket) {
            bucket.prepare(epochSecond);
            bucket.requests++;
            bucket.durationMs += Math.max(0, durationMs);
            if (status >= 400 && status < 500) bucket.clientErrors++;
            if (status >= 500) bucket.serverErrors++;
            bucket.latencyCounts[latencyIndex(durationMs)]++;
        }
    }

    public HttpSnapshot snapshot() {
        long now = clock.instant().getEpochSecond();
        long requests = 0, durationMs = 0, clientErrors = 0, serverErrors = 0;
        long[] latencyCounts = new long[LATENCY_BOUNDS_MS.length];
        for (Bucket bucket : buckets) {
            synchronized (bucket) {
                if (bucket.epochSecond < now - WINDOW_SECONDS + 1 || bucket.epochSecond > now) continue;
                requests += bucket.requests;
                durationMs += bucket.durationMs;
                clientErrors += bucket.clientErrors;
                serverErrors += bucket.serverErrors;
                for (int index = 0; index < latencyCounts.length; index++) {
                    latencyCounts[index] += bucket.latencyCounts[index];
                }
            }
        }
        double average = requests == 0 ? 0 : (double) durationMs / requests;
        double errorRate = requests == 0 ? 0 : (double) (clientErrors + serverErrors) * 100 / requests;
        return new HttpSnapshot(inFlight.get(), requests, requests / 60.0, round(average),
                percentile95(latencyCounts, requests), clientErrors, serverErrors, round(errorRate));
    }

    private int latencyIndex(long durationMs) {
        for (int index = 0; index < LATENCY_BOUNDS_MS.length; index++) {
            if (durationMs <= LATENCY_BOUNDS_MS[index]) return index;
        }
        return LATENCY_BOUNDS_MS.length - 1;
    }

    private long percentile95(long[] counts, long total) {
        if (total == 0) return 0;
        long target = (long) Math.ceil(total * .95);
        long cumulative = 0;
        for (int index = 0; index < counts.length; index++) {
            cumulative += counts[index];
            if (cumulative >= target) {
                long bound = LATENCY_BOUNDS_MS[index];
                return bound == Long.MAX_VALUE ? 5_001 : bound;
            }
        }
        return 0;
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    private static final class Bucket {
        long epochSecond = Long.MIN_VALUE;
        long requests;
        long durationMs;
        long clientErrors;
        long serverErrors;
        final long[] latencyCounts = new long[LATENCY_BOUNDS_MS.length];

        void prepare(long second) {
            if (epochSecond == second) return;
            epochSecond = second;
            requests = durationMs = clientErrors = serverErrors = 0;
            java.util.Arrays.fill(latencyCounts, 0);
        }
    }

    public record HttpSnapshot(int inFlight, long requestsLastMinute, double requestsPerSecond,
                               double averageLatencyMs, long p95LatencyMs, long clientErrors,
                               long serverErrors, double errorRatePercent) { }
}
