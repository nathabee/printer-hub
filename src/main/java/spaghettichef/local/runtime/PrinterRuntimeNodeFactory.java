package spaghettichef.local.runtime;

import spaghettichef.shared.OperationMessages;
import spaghettichef.local.PrinterPort;
import spaghettichef.local.SerialConnection;
import spaghettichef.local.config.SerialDefaults;
import spaghettichef.shared.config.RuntimeDefaults;
import spaghettichef.local.serial.SimulatedPrinterPort;

import java.util.Locale;

public final class PrinterRuntimeNodeFactory {

    private PrinterRuntimeNodeFactory() {
    }

    public static PrinterRuntimeNode create(
            String id,
            String displayName,
            String portName,
            String mode,
            boolean enabled
    ) {
        return create(
                id,
                displayName,
                portName,
                mode,
                defaultStorageDirectory(id),
                enabled);
    }

    public static PrinterRuntimeNode create(
            String id,
            String displayName,
            String portName,
            String mode,
            String storageDirectory,
            boolean enabled
    ) {
        validateRequired("id", id);
        validateRequired("displayName", displayName);
        validateRequired("portName", portName);
        validateRequired("mode", mode);
        validateRequired("storageDirectory", storageDirectory);

        PrinterPort printerPort = createPort(portName, mode);

        return new PrinterRuntimeNode(
                id.trim(),
                displayName.trim(),
                portName.trim(),
                mode.trim(),
                printerStorageDirectory(storageDirectory, id),
                printerPort,
                enabled
        );
    }

    private static PrinterPort createPort(String portName, String mode) {
        String normalizedPortName = portName.trim();
        String normalizedMode = mode.trim().toLowerCase(Locale.ROOT);

        if ("real".equals(normalizedMode)) {
            return new SerialConnection(normalizedPortName, SerialDefaults.DEFAULT_BAUD_RATE);
        }

        if ("sim".equals(normalizedMode)
                || "simulated".equals(normalizedMode)
                || "sim-disconnected".equals(normalizedMode)
                || "sim-timeout".equals(normalizedMode)
                || "sim-error".equals(normalizedMode)) {
            return new SimulatedPrinterPort(normalizedPortName, normalizedMode);
        }

        throw new IllegalArgumentException(OperationMessages.INVALID_PRINTER_MODE);
    }

    private static void validateRequired(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank(fieldName));
        }
    }

    public static String defaultStorageDirectory(String printerId) {
        validateRequired("id", printerId);
        return RuntimeDefaults.DEFAULT_PRINTER_STORAGE_DIRECTORY + "/" + safePathSegment(printerId);
    }

    public static String printerStorageDirectory(String configuredStorageBaseDirectory, String printerId) {
        validateRequired("storageDirectory", configuredStorageBaseDirectory);
        validateRequired("id", printerId);

        String trimmedDirectory = configuredStorageBaseDirectory.trim();
        String safePrinterId = safePathSegment(printerId);
        String normalizedDirectory = trimmedDirectory.replace('\\', '/');

        if (lastPathSegment(normalizedDirectory).equals(safePrinterId)) {
            return trimmedDirectory;
        }

        String separator = trimmedDirectory.contains("\\") && !trimmedDirectory.contains("/") ? "\\" : "/";
        String withoutTrailingSeparator = trimmedDirectory.replaceAll("[/\\\\]+$", "");
        if (withoutTrailingSeparator.isBlank()) {
            return trimmedDirectory + safePrinterId;
        }

        return withoutTrailingSeparator + separator + safePrinterId;
    }

    private static String safePathSegment(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String lastPathSegment(String value) {
        String withoutTrailingSeparator = value.replaceAll("/+$", "");
        int separatorIndex = withoutTrailingSeparator.lastIndexOf('/');
        return separatorIndex >= 0
                ? withoutTrailingSeparator.substring(separatorIndex + 1)
                : withoutTrailingSeparator;
    }
}
