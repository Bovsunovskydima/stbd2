package ua.lab.cafenet.nosql.bench;

import java.util.Arrays;

public record Measurement(int iterations,
                          long elementsPerCall,
                          double avgMs,
                          double p50Ms,
                          double p95Ms,
                          double minMs,
                          double maxMs,
                          double opsPerSecond) {

    static Measurement of(long[] nanos, long elementsPerCall) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);

        long sum = 0;
        for (long value : sorted) {
            sum += value;
        }
        double avgMs = toMs(sum / (double) sorted.length);

        return new Measurement(
                sorted.length,
                elementsPerCall,
                avgMs,
                toMs(percentile(sorted, 0.50)),
                toMs(percentile(sorted, 0.95)),
                toMs(sorted[0]),
                toMs(sorted[sorted.length - 1]),
                avgMs == 0 ? 0 : round(1000.0 / avgMs));
    }

    private static double percentile(long[] sorted, double share) {
        int index = (int) Math.ceil(share * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static double toMs(double nanos) {
        return round(nanos / 1_000_000.0);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
