package spaghettichef.central.service;

import spaghettichef.central.persistence.CentralFarmStore;
import spaghettichef.central.persistence.CentralReplayPackageStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CentralReplayPackageService {
    private final CentralFarmStore farmStore;
    private final CentralReplayPackageStore replayStore;
    private final Clock clock;

    public CentralReplayPackageService(CentralFarmStore farmStore, CentralReplayPackageStore replayStore) {
        this(farmStore, replayStore, Clock.systemUTC());
    }

    public CentralReplayPackageService(
            CentralFarmStore farmStore,
            CentralReplayPackageStore replayStore,
            Clock clock) {
        this.farmStore = farmStore;
        this.replayStore = replayStore;
        this.clock = clock;
    }

    public CentralReplayUploadRequest createUpload(
            String farmId,
            String runtimeInstanceId,
            String farmSecret,
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
            List<UploadedReplayFile> uploadedFiles) {
        require(farmId, "farmId");
        require(runtimeInstanceId, "runtimeInstanceId");
        require(farmSecret, "farmSecret");

        CentralFarm farm = farmStore.findByFarmId(farmId.trim());
        if (farm == null) {
            throw new CentralFarmService.FarmRejectedException("unknown_farm");
        }
        if (!farm.enabled()) {
            throw new CentralFarmService.FarmRejectedException("farm_disabled");
        }
        if (!farm.runtimeInstanceId().equals(runtimeInstanceId.trim())) {
            throw new CentralFarmService.FarmRejectedException("runtime_instance_mismatch");
        }
        if (!farm.farmSecret().equals(farmSecret.trim())) {
            throw new CentralFarmService.FarmRejectedException("farm_secret_mismatch");
        }
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            throw new IllegalArgumentException("replay package must contain files");
        }

        Instant now = clock.instant();
        String packageId = "replay-" + UUID.randomUUID();
        CentralReplayPackage replayPackage = new CentralReplayPackage(
                packageId,
                farm.farmId(),
                farm.runtimeInstanceId(),
                blankToNull(cameraJobId),
                blankToNull(printerId),
                blankToNull(cameraId),
                blankToNull(label),
                blankToNull(startedAt),
                blankToNull(finishedAt),
                Math.max(0, frameCount),
                Math.max(0, deltaCount),
                blankToNull(visibility),
                manifestJson,
                now,
                now);
        List<CentralReplayFile> files = uploadedFiles.stream()
                .map(file -> new CentralReplayFile(
                        packageId,
                        fileType(file.relativePath()),
                        file.relativePath(),
                        contentType(file.relativePath()),
                        file.sizeBytes(),
                        now))
                .toList();
        return new CentralReplayUploadRequest(replayPackage, files);
    }

    public void store(CentralReplayUploadRequest request) {
        replayStore.insert(request);
    }

    public List<CentralReplayPackage> listForFarm(String farmId) {
        CentralFarm farm = farmStore.findByFarmId(farmId);
        if (farm == null) {
            throw new CentralFarmService.FarmRejectedException("unknown_farm");
        }
        return replayStore.findByFarmId(farmId);
    }

    public CentralReplayPackage getPackage(String packageId) {
        require(packageId, "packageId");
        CentralReplayPackage replayPackage = replayStore.findByPackageId(packageId.trim());
        if (replayPackage == null) {
            throw new CentralFarmService.FarmRejectedException("unknown_replay_package");
        }
        return replayPackage;
    }

    public List<CentralReplayFile> listFiles(String packageId) {
        getPackage(packageId);
        return replayStore.findFiles(packageId);
    }

    private String fileType(String relativePath) {
        if (relativePath.startsWith("snapshots/")) {
            return "snapshot";
        }
        if (relativePath.startsWith("deltas/")) {
            return "delta";
        }
        if (relativePath.startsWith("analysis/")) {
            return "analysis";
        }
        if ("manifest.json".equals(relativePath)) {
            return "manifest";
        }
        return "other";
    }

    private String contentType(String relativePath) {
        String lower = relativePath.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        return "application/octet-stream";
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UploadedReplayFile(String relativePath, long sizeBytes) {
    }
}
