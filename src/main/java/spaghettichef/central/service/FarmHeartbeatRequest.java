package spaghettichef.central.service;

public record FarmHeartbeatRequest(
        String farmId,
        String runtimeInstanceId,
        String farmSecret,
        String runtimeVersion,
        String status,
        int printerCount,
        int cameraCount,
        int activePrintCount,
        int warningCount,
        int errorCount,
        int spaghettiAlertCount,
        String message) {
}
