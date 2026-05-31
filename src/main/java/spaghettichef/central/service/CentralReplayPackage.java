package spaghettichef.central.service;

import java.time.Instant;

public record CentralReplayPackage(
        String packageId,
        String farmId,
        String runtimeInstanceId,
        String cameraJobId,
        String printerId,
        String cameraId,
        String label,
        String startedAt,
        String finishedAt,
        int frameCount,
        int deltaCount,
        String visibility,
        String manifestJson,
        Instant createdAt,
        Instant updatedAt) {
}
