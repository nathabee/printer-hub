package spaghettichef.central.service;

public record FarmRegistrationRequest(
        String runtimeInstanceId,
        String farmName,
        String runtimeVersion,
        String hostname,
        String displayLocation,
        String description,
        String metadataJson) {
}
