package ua.lab.cafenet.nosql.bench;

import java.time.LocalDateTime;
import java.util.List;

public record BenchmarkReport(LocalDateTime startedAt,
                              int reviewsLoaded,
                              int iterations,
                              int warmup,
                              long preparationMs,
                              List<ScenarioComparison> scenarios) {
}
