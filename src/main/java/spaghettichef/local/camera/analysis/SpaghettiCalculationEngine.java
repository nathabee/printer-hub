package spaghettichef.local.camera.analysis;

import spaghettichef.local.persistence.CameraDeltaFrame;

public interface SpaghettiCalculationEngine {

    String engineName();

    String algorithmVariant();

    String engineVersion();

    CalculationEngineStatus status();

    CalculationEngineResult analyze(CameraDeltaFrame frame, double confidenceThreshold);
}
