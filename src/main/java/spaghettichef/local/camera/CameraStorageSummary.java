package spaghettichef.local.camera;

public record CameraStorageSummary(
        String printerId,
        String storageRoot,
        int cameraJobCount,
        int snapshotCount,
        int retainedSnapshotCount,
        int deltaSetCount,
        int deltaFrameCount,
        int calculationRunCount,
        int calculationResultCount,
        long totalSnapshotBytes,
        long totalDeltaBytes,
        int missingFileCount,
        boolean latestSnapshotAvailable,
        boolean previousSnapshotAvailable,
        boolean deltaPreviewAvailable,
        String message) {
}
