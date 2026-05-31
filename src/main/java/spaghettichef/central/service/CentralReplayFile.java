package spaghettichef.central.service;

import java.time.Instant;

public record CentralReplayFile(
        String packageId,
        String fileType,
        String relativePath,
        String contentType,
        long sizeBytes,
        Instant createdAt) {
}
