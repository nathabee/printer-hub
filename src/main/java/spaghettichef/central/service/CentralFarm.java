package spaghettichef.central.service;

import java.time.Instant;

public record CentralFarm(
        String farmId,
        String runtimeInstanceId,
        String farmName,
        String runtimeVersion,
        String hostname,
        String displayLocation,
        String description,
        String farmSecret,
        boolean enabled,
        Instant registeredAt,
        Instant lastSeenAt,
        int printerCount,
        int cameraCount,
        int activePrintCount,
        int warningCount,
        int errorCount,
        int spaghettiAlertCount,
        String lastStatusMessage,
        String lastSummaryJson,
        Instant createdAt,
        Instant updatedAt,
        String metadataJson) {
}
