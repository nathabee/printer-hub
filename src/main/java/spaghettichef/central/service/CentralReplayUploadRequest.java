package spaghettichef.central.service;

import java.util.List;

public record CentralReplayUploadRequest(
        CentralReplayPackage replayPackage,
        List<CentralReplayFile> files) {
}
