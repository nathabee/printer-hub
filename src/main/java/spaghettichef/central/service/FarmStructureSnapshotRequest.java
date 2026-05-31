package spaghettichef.central.service;

public record FarmStructureSnapshotRequest(
        String farmId,
        String runtimeInstanceId,
        String farmSecret,
        String structureJson) {
}
