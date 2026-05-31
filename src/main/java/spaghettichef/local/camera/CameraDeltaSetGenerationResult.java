package spaghettichef.local.camera;

import spaghettichef.local.persistence.CameraDeltaSet;

public record CameraDeltaSetGenerationResult(
        CameraDeltaSet deltaSet,
        int sourceSnapshotCount,
        int generatedDeltaCount,
        int skippedIntermediateSnapshotCount
) {
}
