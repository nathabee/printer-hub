package spaghettichef.local.camera;

import java.nio.file.Path;
import spaghettichef.shared.config.RuntimeDefaults;
import spaghettichef.local.persistence.DatabaseConfig;
import spaghettichef.local.persistence.PrinterConfigurationStore;

public final class CameraStoragePaths {

    private CameraStoragePaths() {
    }

    public static Path defaultBaseDirectory() {
        return DatabaseConfig.dataDirectory().resolve(RuntimeDefaults.DEFAULT_PRINTER_STORAGE_DIRECTORY).normalize();
    }

    public static Path resolveBaseDirectory(String configuredStorageDirectory) {
        String selectedDirectory = configuredStorageDirectory == null || configuredStorageDirectory.isBlank()
                ? RuntimeDefaults.DEFAULT_PRINTER_STORAGE_DIRECTORY
                : configuredStorageDirectory.trim();

        Path selectedPath = Path.of(selectedDirectory);
        if (selectedPath.isAbsolute()) {
            return selectedPath.normalize();
        }

        if (isLegacyWorkingDirectoryDefault(selectedDirectory)) {
            return defaultBaseDirectory();
        }

        return DatabaseConfig.dataDirectory().resolve(selectedPath).normalize();
    }

    public static Path printerDirectory(String configuredStorageDirectory, String printerId) {
        return resolveBaseDirectory(configuredStorageDirectory)
                .normalize();
    }

    public static Path printerDirectory(String printerId) {
        return printerDirectory(printerStorageDirectory(printerId), printerId);
    }

    public static Path cameraDirectory(String configuredPrinterStorageDirectory, String printerId) {
        return printerDirectory(configuredPrinterStorageDirectory, printerId)
                .resolve(RuntimeDefaults.CAMERA_STORAGE_SUBDIRECTORY)
                .normalize();
    }

    public static Path cameraDirectory(String printerId) {
        return cameraDirectory(printerStorageDirectory(printerId), printerId);
    }

    public static Path snapshotsDirectory(String configuredStorageDirectory, String printerId, long cameraJobId) {
        return cameraDirectory(configuredStorageDirectory, printerId)
                .resolve("snapshots")
                .resolve(cameraJobSegment(cameraJobId))
                .normalize();
    }

    public static Path snapshotsDirectory(String printerId, long cameraJobId) {
        return snapshotsDirectory(printerStorageDirectory(printerId), printerId, cameraJobId);
    }

    public static Path snapshotPathForEntryId(
            String configuredStorageDirectory,
            String printerId,
            long cameraJobId,
            long snapshotEntryId,
            String extension) {
        String normalizedExtension = normalizeExtension(extension);
        if (snapshotEntryId <= 0L) {
            throw new IllegalArgumentException("snapshotEntryId must be greater than zero");
        }

        String fileName = "%06d_snapshot%s".formatted(snapshotEntryId, normalizedExtension);

        return snapshotsDirectory(configuredStorageDirectory, printerId, cameraJobId)
                .resolve(fileName)
                .normalize();
    }

    public static Path snapshotPathForEntryId(
            String printerId,
            long cameraJobId,
            long snapshotEntryId,
            String extension) {
        return snapshotPathForEntryId(
                printerStorageDirectory(printerId),
                printerId,
                cameraJobId,
                snapshotEntryId,
                extension);
    }

    public static Path deltasDirectory(
            String configuredStorageDirectory,
            String printerId,
            long cameraJobId,
            long deltaSetId) {
        return cameraDirectory(configuredStorageDirectory, printerId)
                .resolve("deltas")
                .resolve(cameraJobSegment(cameraJobId))
                .resolve(deltaSetSegment(deltaSetId))
                .normalize();
    }

    public static Path deltasDirectory(String printerId, long cameraJobId, long deltaSetId) {
        return deltasDirectory(printerStorageDirectory(printerId), printerId, cameraJobId, deltaSetId);
    }

    public static Path deltaFramePath(
            String configuredStorageDirectory,
            String printerId,
            long cameraJobId,
            long deltaSetId,
            int fromSequence,
            int toSequence) {
        if (fromSequence <= 0 || toSequence <= 0) {
            throw new IllegalArgumentException("delta frame sequence values must be greater than zero");
        }

        String fileName = "%06d_%06d_delta.jpg".formatted(fromSequence, toSequence);
        return deltasDirectory(configuredStorageDirectory, printerId, cameraJobId, deltaSetId)
                .resolve(fileName)
                .normalize();
    }

    public static Path deltaFramePath(
            String printerId,
            long cameraJobId,
            long deltaSetId,
            int fromSequence,
            int toSequence) {
        return deltaFramePath(
                printerStorageDirectory(printerId),
                printerId,
                cameraJobId,
                deltaSetId,
                fromSequence,
                toSequence);
    }

    private static boolean isLegacyWorkingDirectoryDefault(String value) {
        String normalized = value.replace('\\', '/');
        return "data/printers".equals(normalized);
    }

    private static String printerStorageDirectory(String printerId) {
        String normalizedPrinterId = safePathSegment(printerId, "printerId");
        return new PrinterConfigurationStore()
                .findAll()
                .stream()
                .filter(node -> node.id().equals(normalizedPrinterId))
                .findFirst()
                .map(node -> node.storageDirectory())
                .orElse(RuntimeDefaults.DEFAULT_PRINTER_STORAGE_DIRECTORY + "/" + normalizedPrinterId);
    }

    private static String cameraJobSegment(long cameraJobId) {
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }

        return Long.toString(cameraJobId);
    }

    private static String deltaSetSegment(long deltaSetId) {
        if (deltaSetId <= 0L) {
            throw new IllegalArgumentException("deltaSetId must be greater than zero");
        }

        return Long.toString(deltaSetId);
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("extension must not be blank");
        }

        String normalized = extension.trim();
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }

    private static String safePathSegment(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        String normalized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return normalized;
    }
}
