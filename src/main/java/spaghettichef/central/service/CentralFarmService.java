package spaghettichef.central.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import spaghettichef.central.persistence.CentralFarmStore;

public final class CentralFarmService {
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);
    private static final Duration OFFLINE_AFTER = Duration.ofMinutes(10);

    private final CentralFarmStore store;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public CentralFarmService(CentralFarmStore store) {
        this(store, Clock.systemUTC());
    }

    public CentralFarmService(CentralFarmStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public CentralFarm register(FarmRegistrationRequest request) {
        require(request.runtimeInstanceId(), "runtimeInstanceId");
        require(request.farmName(), "farmName");

        CentralFarm existing = store.findByRuntimeInstanceId(request.runtimeInstanceId());
        Instant now = clock.instant();
        if (existing != null) {
            CentralFarm updated = new CentralFarm(
                    existing.farmId(),
                    existing.runtimeInstanceId(),
                    request.farmName().trim(),
                    blankToNull(request.runtimeVersion()),
                    blankToNull(request.hostname()),
                    blankToNull(request.displayLocation()),
                    blankToNull(request.description()),
                    existing.farmSecret(),
                    existing.enabled(),
                    existing.registeredAt(),
                    existing.lastSeenAt(),
                    existing.printerCount(),
                    existing.cameraCount(),
                    existing.activePrintCount(),
                    existing.warningCount(),
                    existing.errorCount(),
                    existing.spaghettiAlertCount(),
                    existing.lastStatusMessage(),
                    existing.lastSummaryJson(),
                    existing.createdAt(),
                    now,
                    request.metadataJson());
            store.updateRegistration(updated);
            return updated;
        }

        String farmId = "farm-" + UUID.randomUUID();
        String secret = newSecret();
        CentralFarm created = new CentralFarm(
                farmId,
                request.runtimeInstanceId().trim(),
                request.farmName().trim(),
                blankToNull(request.runtimeVersion()),
                blankToNull(request.hostname()),
                blankToNull(request.displayLocation()),
                blankToNull(request.description()),
                secret,
                true,
                now,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                now,
                now,
                request.metadataJson());
        store.insert(created);
        return created;
    }

    public CentralFarm heartbeat(FarmHeartbeatRequest request) {
        require(request.farmId(), "farmId");
        require(request.runtimeInstanceId(), "runtimeInstanceId");
        require(request.farmSecret(), "farmSecret");

        CentralFarm existing = store.findByFarmId(request.farmId());
        if (existing == null) {
            throw new FarmRejectedException("unknown_farm");
        }
        if (!existing.enabled()) {
            throw new FarmRejectedException("farm_disabled");
        }
        if (!existing.runtimeInstanceId().equals(request.runtimeInstanceId().trim())) {
            throw new FarmRejectedException("runtime_instance_mismatch");
        }
        if (!existing.farmSecret().equals(request.farmSecret().trim())) {
            throw new FarmRejectedException("farm_secret_mismatch");
        }

        Instant now = clock.instant();
        CentralFarm updated = new CentralFarm(
                existing.farmId(),
                existing.runtimeInstanceId(),
                existing.farmName(),
                blankToNull(request.runtimeVersion()),
                existing.hostname(),
                existing.displayLocation(),
                existing.description(),
                existing.farmSecret(),
                existing.enabled(),
                existing.registeredAt(),
                now,
                Math.max(0, request.printerCount()),
                Math.max(0, request.cameraCount()),
                Math.max(0, request.activePrintCount()),
                Math.max(0, request.warningCount()),
                Math.max(0, request.errorCount()),
                Math.max(0, request.spaghettiAlertCount()),
                blankToNull(request.message()),
                summaryJson(request),
                existing.createdAt(),
                now,
                existing.metadataJson());
        store.updateHeartbeat(updated);
        return updated;
    }

    public List<CentralFarmOverview> overview() {
        return store.findAll().stream()
                .map(farm -> new CentralFarmOverview(farm, derivedStatus(farm)))
                .toList();
    }

    public String derivedStatus(CentralFarm farm) {
        if (!farm.enabled()) {
            return "DISABLED";
        }
        if (farm.lastSeenAt() == null) {
            return "OFFLINE";
        }
        Duration age = Duration.between(farm.lastSeenAt(), clock.instant());
        if (age.compareTo(OFFLINE_AFTER) > 0) {
            return "OFFLINE";
        }
        if (age.compareTo(STALE_AFTER) > 0) {
            return "STALE";
        }
        return "ONLINE";
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String summaryJson(FarmHeartbeatRequest request) {
        return "{"
                + "\"reportedStatus\":\"" + escape(blankToNull(request.status())) + "\","
                + "\"printerCount\":" + Math.max(0, request.printerCount()) + ","
                + "\"cameraCount\":" + Math.max(0, request.cameraCount()) + ","
                + "\"activePrintCount\":" + Math.max(0, request.activePrintCount()) + ","
                + "\"warningCount\":" + Math.max(0, request.warningCount()) + ","
                + "\"errorCount\":" + Math.max(0, request.errorCount()) + ","
                + "\"spaghettiAlertCount\":" + Math.max(0, request.spaghettiAlertCount())
                + "}";
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class FarmRejectedException extends RuntimeException {
        public FarmRejectedException(String message) {
            super(message);
        }
    }
}
