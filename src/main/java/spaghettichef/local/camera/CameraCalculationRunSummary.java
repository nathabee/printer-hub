package spaghettichef.local.camera;

import spaghettichef.local.persistence.CameraCalculationRun;

public record CameraCalculationRunSummary(
        CameraCalculationRun run,
        int resultCount,
        int suspectedCount,
        double averageConfidence,
        Double resultsPerSecond,
        Double averageMillisecondsPerFrame
) {
}
