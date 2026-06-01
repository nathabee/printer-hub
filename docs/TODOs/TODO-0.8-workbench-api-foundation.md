# 0.8.x — Dataset And Engine Workbench API Foundation

The `0.8.x` series prepares the existing SpaghettiChef camera, dataset, delta, calculation, and engine APIs for reliable consumption by the external BenchChef workbench.

The current implementation already provides many camera/admin API endpoints for camera jobs, snapshots, delta sets, delta frames, calculation runs, calculation results, traces, comparisons, visual inspection, and engine settings.

The purpose of `0.8.x` is therefore:

```text
verify the existing API surface
document the BenchChef-compatible contract
add automated API tests
add missing dataset-level aggregation endpoints
add missing label and validation endpoints
add missing report/export endpoints
prepare controlled parameter sweep and ML export APIs
```

SpaghettiChef remains the data and execution provider. BenchChef later consumes this API for dataset browsing, benchmark review, comparison dashboards, report generation, parameter sweeps, and ML preparation.

---

## 0.8.0 — Existing API Verification And Workbench Contract

### Status

Planned.

### Purpose

Verify that the existing SpaghettiChef camera/admin APIs are available, documented, and stable enough for BenchChef.

This step checks what is already implemented and turns it into an explicit external workbench contract.

### Existing API Groups To Verify

Verify these endpoint groups:

```text
camera snapshot jobs
camera snapshot timelines
snapshot file access
camera storage synchronization
delta set listing and generation
delta frame listing
delta frame file access
calculation run listing and creation
calculation result listing
calculation trace rows
calculation run comparison
calculation result visual inspection
calculation engine settings
```

### Endpoints To Verify

```text
GET    /admin/camera/snapshot/jobs
GET    /admin/camera/snapshot/jobs?printerId={printerId}
GET    /admin/camera/snapshot/jobs/{cameraJobKey}
GET    /admin/camera/snapshot/jobs/{cameraJobKey}/timeline
GET    /admin/camera/snapshot/files/{snapshotEntryId}

POST   /admin/camera/storage/{printerId}/sync

GET    /admin/camera/snapshot/jobs/{cameraJobId}/delta-sets
POST   /admin/camera/snapshot/jobs/{cameraJobId}/delta-sets

GET    /admin/camera/delta-sets/{deltaSetId}
GET    /admin/camera/delta-sets/{deltaSetId}/frames
GET    /admin/camera/delta-sets/{deltaSetId}/calculation-runs
POST   /admin/camera/delta-sets/{deltaSetId}/calculation-runs

GET    /admin/camera/delta-frames/{deltaFrameId}/file?printerId={printerId}

GET    /admin/camera/calculation-engine-settings
PUT    /admin/camera/calculation-engine-settings/{engineName}

GET    /admin/camera/calculation-runs/{calculationRunId}
GET    /admin/camera/calculation-runs/{calculationRunId}/results
GET    /admin/camera/calculation-runs/{calculationRunId}/trace
GET    /admin/camera/calculation-runs/{calculationRunId}/compare?rightRunId={rightRunId}

GET    /admin/camera/calculation-results/{calculationResultId}/visual?printerId={printerId}
```

### Work To Do

Create or update API tests that verify:

```text
each endpoint exists
each endpoint returns the expected HTTP status
each endpoint returns valid JSON or image bytes
required ids are present in JSON responses
missing resources return controlled errors
image endpoints return image content with no-store cache headers
admin/security guards still apply where relevant
```

Create a workbench API contract document with:

```text
endpoint
HTTP method
purpose
required query parameters
request body, if any
response shape
main ids returned
error cases
BenchChef usage note
```

### Acceptance Criteria

* The current camera/admin API surface is verified by automated tests.
* The workbench API contract document exists.
* BenchChef can rely on the documented endpoint names.
* Response payloads contain the ids needed for navigation between camera jobs, snapshots, delta sets, delta frames, calculation runs, calculation results, and visual inspection.
* Image endpoints are tested.
* Error responses for missing snapshots, missing delta frames, missing calculation runs, and invalid ids are tested.
* Existing dashboard behavior remains working.
* `mvn test` passes.

---

## 0.8.1 — Dataset Summary API

### Status

Planned.

### Purpose

Add dataset-level summary endpoints for BenchChef.

The existing API exposes camera jobs and calculation data. BenchChef also needs dataset-level summaries such as dataset version, printer count, job count, label counts, missing file count, and metadata validation status.

### Endpoints To Add

```text
GET /admin/camera/datasets
GET /admin/camera/datasets/{datasetId}
GET /admin/camera/datasets/{datasetId}/summary
```

### Work To Do

Implement dataset summary responses using existing camera storage, dataset metadata, and camera admin data.

The summary should include:

```text
datasetId
datasetVersion
layoutVersion
contentRoot
metadataRoot
sourceType
printerCount
cameraJobCount
sourceSnapshotCount
deltaSetCount
deltaFrameCount
calculationRunCount
calculationResultCount
labelCounts
missingFileCount
metadataErrorCount
createdAt
importedAt
message
```

### Suggested Dataset Summary Response

```json
{
  "datasetId": "spaghetti-dataset-001",
  "datasetVersion": "2026-06-01",
  "layoutVersion": "0.7.0",
  "contentRoot": "dataset",
  "metadataRoot": "dataset/json",
  "sourceType": "imported-dataset",
  "printerCount": 1,
  "cameraJobCount": 3,
  "sourceSnapshotCount": 350,
  "deltaSetCount": 4,
  "deltaFrameCount": 346,
  "calculationRunCount": 8,
  "calculationResultCount": 2768,
  "labelCounts": {
    "normal": 2,
    "spaghetti": 1,
    "unclear": 0
  },
  "missingFileCount": 0,
  "metadataErrorCount": 0,
  "message": "ok"
}
```

### Tests To Add

Test:

```text
dataset list returns at least the local runtime dataset when camera data exists
dataset summary returns expected counts
unknown dataset id returns a controlled 404
empty dataset state returns valid empty counts
imported dataset metadata is reflected when available
```

### Acceptance Criteria

* BenchChef can list datasets.
* BenchChef can read one dataset summary.
* Dataset summary includes counts for jobs, snapshots, delta sets, delta frames, calculation runs, and labels.
* Empty datasets are represented cleanly.
* Unknown dataset ids return controlled errors.
* `mvn test` passes.

---

## 0.8.2 — Dataset Labels API

### Status

Planned.

### Purpose

Expose dataset labels for BenchChef reports and benchmark comparison.

The dataset labels are needed to classify calculation results as true positives, true negatives, false positives, false negatives, unclear, or unlabeled.

### Endpoints To Add

```text
GET /admin/camera/datasets/{datasetId}/labels
```

Optional filter support:

```text
GET /admin/camera/datasets/{datasetId}/labels?printerId={printerId}
GET /admin/camera/datasets/{datasetId}/labels?cameraJobId={cameraJobId}
```

### Work To Do

Read dataset label metadata and return job-level labels.

Supported labels:

```text
normal
spaghetti
unclear
```

Optional failure stages:

```text
none
early
clear
severe
unknown
```

### Suggested Response

```json
{
  "datasetId": "spaghetti-dataset-001",
  "labels": [
    {
      "printerId": "pex01",
      "cameraJobId": 1,
      "label": "normal",
      "failureStage": "none",
      "source": "dataset/json/pex01/jobs/job-1/label.json",
      "message": null
    },
    {
      "printerId": "pex01",
      "cameraJobId": 2,
      "label": "spaghetti",
      "failureStage": "clear",
      "source": "dataset/json/pex01/jobs/job-2/label.json",
      "message": null
    }
  ],
  "labelCounts": {
    "normal": 1,
    "spaghetti": 1,
    "unclear": 0
  }
}
```

### Tests To Add

Test:

```text
labels endpoint returns labels from dataset metadata
normal, spaghetti, and unclear are counted correctly
unknown label values are reported as validation issues
cameraJobId filter works
printerId filter works
missing label file is represented clearly
```

### Acceptance Criteria

* BenchChef can read dataset labels.
* BenchChef can calculate label distribution.
* Job-level labels can be matched to camera jobs.
* Missing labels are visible.
* Invalid labels are visible through validation.
* `mvn test` passes.

---

## 0.8.3 — Dataset Validation API

### Status

Planned.

### Purpose

Expose dataset quality and consistency checks for BenchChef.

BenchChef needs to know whether a dataset is usable before running comparisons or parameter sweeps.

### Endpoint To Add

```text
GET /admin/camera/datasets/{datasetId}/validation
```

### Work To Do

Implement validation checks for:

```text
missing source snapshot files
missing delta files
metadata rows pointing to missing files
files without metadata, if detectable
unknown labels
invalid label values
camera jobs without snapshots
delta sets without frames
calculation runs without results
calculation results without delta frames
engine names not known in current settings
method names not known in current settings
```

### Suggested Response

```json
{
  "datasetId": "spaghetti-dataset-001",
  "valid": false,
  "summary": {
    "issueCount": 2,
    "errorCount": 1,
    "warningCount": 1,
    "missingFileCount": 1,
    "metadataErrorCount": 1
  },
  "issues": [
    {
      "severity": "ERROR",
      "type": "MISSING_DELTA_FILE",
      "printerId": "pex01",
      "cameraJobId": 1,
      "deltaSetId": 1,
      "deltaFrameId": 10,
      "path": "dataset/pex01/deltas/1/1/000100_000101_delta.jpg",
      "message": "Delta file is missing"
    },
    {
      "severity": "WARNING",
      "type": "UNKNOWN_LABEL",
      "printerId": "pex01",
      "cameraJobId": 2,
      "path": "dataset/json/pex01/jobs/job-2/label.json",
      "message": "Unknown label value"
    }
  ]
}
```

### Tests To Add

Test:

```text
valid dataset returns valid true
missing source snapshot creates validation issue
missing delta frame creates validation issue
camera job without snapshots creates validation issue
delta set without frames creates validation issue
calculation run without results creates validation issue
invalid label creates validation issue
unknown engine name creates validation issue
```

### Acceptance Criteria

* BenchChef can display dataset validation status.
* Validation issues include enough ids to navigate back to the affected item.
* Validation distinguishes errors and warnings.
* Missing files are counted.
* Invalid metadata is counted.
* `mvn test` passes.

---

## 0.8.4 — Engine Availability Verification

### Status

Planned.

### Purpose

Verify the existing calculation engine settings endpoint and add availability checks if needed.

The existing endpoint is:

```text
GET /admin/camera/calculation-engine-settings
```

It exposes persisted engine settings.

### Work To Do

Verify the existing endpoint returns:

```text
engineName
adapterType
engineLabel
enabled
defaultMethodName
defaultConfidenceThreshold
defaultParameterJson
defaultCliMethod
executablePath
timeoutMs
sortOrder
createdAt
updatedAt
```

Add computed availability fields where useful.

Either extend the existing response safely or add:

```text
GET /admin/camera/workbench/engine-capabilities
```

Use the capability endpoint when computed values should stay separate from persisted settings.

### Suggested Capability Response

```json
{
  "engines": [
    {
      "engineName": "JAVA_BASIC_DELTA",
      "engineLabel": "Java basic delta",
      "adapterType": "JAVA_BASIC_DELTA",
      "enabled": true,
      "available": true,
      "availabilityMessage": "available",
      "defaultMethodName": "spaghetti-heuristic",
      "defaultConfidenceThreshold": 0.85,
      "supportedInputTypes": ["DELTA_FRAME"],
      "supportedMethods": ["spaghetti-heuristic"]
    },
    {
      "engineName": "RUST_IMG_ANALYZER",
      "engineLabel": "Rust img-analyzer",
      "adapterType": "EXTERNAL_CLI",
      "enabled": true,
      "available": false,
      "availabilityMessage": "executable path is missing or invalid",
      "defaultMethodName": "spaghetti-heuristic",
      "defaultConfidenceThreshold": 0.85,
      "supportedInputTypes": ["DELTA_FRAME"],
      "supportedMethods": ["delta-basic"]
    }
  ]
}
```

### Tests To Add

Test:

```text
engine settings endpoint returns configured engines
disabled engines are returned
external CLI engine with missing path reports unavailable when capability endpoint exists
Java engine reports available
engineName remains the stable identity
PUT updates settings and GET returns updated values
```

### Acceptance Criteria

* BenchChef can read engine settings.
* BenchChef can distinguish enabled and disabled engines.
* BenchChef can distinguish available and unavailable engines if capability endpoint is implemented.
* External CLI availability is clear.
* Engine settings remain persisted.
* `mvn test` passes.

---

## 0.8.5 — Label-Aware Baseline Report API

### Status

Planned.

### Purpose

Create a report endpoint that combines dataset labels with calculation results.

Existing calculation APIs return engine results. This step adds benchmark classification using expected labels.

### Endpoint To Add

```text
GET /admin/camera/workbench/reports/baseline?calculationRunId={calculationRunId}&datasetId={datasetId}
```

### Work To Do

Generate a label-aware report from:

```text
dataset labels
calculation run metadata
calculation results
delta frames
trace rows
file state
engine metadata
```

Classify rows as:

```text
TRUE_POSITIVE
TRUE_NEGATIVE
FALSE_POSITIVE
FALSE_NEGATIVE
UNCLEAR
UNLABELED
MISSING_FILE
ENGINE_ERROR
```

### Classification Rules

```text
expected label normal + suspected false = TRUE_NEGATIVE
expected label normal + suspected true  = FALSE_POSITIVE

expected label spaghetti + suspected true  = TRUE_POSITIVE
expected label spaghetti + suspected false = FALSE_NEGATIVE

expected label unclear = UNCLEAR
missing expected label = UNLABELED
missing input file = MISSING_FILE
engine failure = ENGINE_ERROR
```

### Suggested Response

```json
{
  "datasetId": "spaghetti-dataset-001",
  "calculationRunId": 20,
  "engineName": "JAVA_BASIC_DELTA",
  "methodName": "spaghetti-heuristic",
  "parameterJson": "{}",
  "confidenceThreshold": 0.85,
  "summary": {
    "inputCount": 199,
    "labeledInputCount": 180,
    "unlabeledInputCount": 19,
    "truePositiveCount": 40,
    "trueNegativeCount": 120,
    "falsePositiveCount": 10,
    "falseNegativeCount": 10,
    "unclearCount": 0,
    "missingFileCount": 0,
    "engineErrorCount": 0,
    "accuracy": 0.8889,
    "precision": 0.8000,
    "recall": 0.8000,
    "averageProcessingTimeMs": 12.5,
    "p95ProcessingTimeMs": 21.0
  },
  "rows": [
    {
      "calculationResultId": 30,
      "deltaFrameId": 10,
      "cameraJobId": 12,
      "expectedLabel": "spaghetti",
      "actualDecision": "spaghetti",
      "classification": "TRUE_POSITIVE",
      "confidence": 0.91,
      "suspected": true,
      "reasonCodes": "[HIGH_DELTA_SCORE]",
      "processingTimeMs": 13,
      "fromSnapshotId": 100,
      "toSnapshotId": 101,
      "fromSnapshotUrl": "/admin/camera/snapshot/files/100",
      "toSnapshotUrl": "/admin/camera/snapshot/files/101",
      "deltaFrameUrl": "/admin/camera/delta-frames/10/file?printerId=pex01",
      "message": "ok"
    }
  ]
}
```

### Tests To Add

Test:

```text
normal + not suspected becomes TRUE_NEGATIVE
normal + suspected becomes FALSE_POSITIVE
spaghetti + suspected becomes TRUE_POSITIVE
spaghetti + not suspected becomes FALSE_NEGATIVE
unclear label becomes UNCLEAR
missing label becomes UNLABELED
missing file becomes MISSING_FILE
summary counts are correct
accuracy, precision, and recall are calculated correctly
```

### Acceptance Criteria

* BenchChef can request a baseline report for a calculation run.
* Report includes summary metrics.
* Report includes row-level classifications.
* False positives and false negatives are visible.
* Image URLs are included for visual inspection.
* Missing labels and unclear labels are handled explicitly.
* `mvn test` passes.

---

## 0.8.6 — Workbench Export API

### Status

Planned.

### Purpose

Provide export-ready JSON for BenchChef reports and comparisons.

### Endpoints To Add

```text
GET /admin/camera/workbench/exports/dataset-summary?datasetId={datasetId}
GET /admin/camera/workbench/exports/calculation-run?calculationRunId={calculationRunId}
GET /admin/camera/workbench/exports/comparison-input?datasetId={datasetId}
```

### Work To Do

Implement read-only exports.

Dataset summary export should include:

```text
dataset metadata
printer list
camera job summaries
label counts
snapshot counts
delta set counts
delta frame counts
calculation run counts
validation summary
```

Calculation run export should include:

```text
calculation run metadata
engine metadata
resolved parameters
result rows
trace rows
image API links
file state
```

Comparison input export should include:

```text
datasetId
datasetVersion
printerId
cameraJobId
deltaSetId
deltaFrameId
expectedLabel
calculationRunId
engineName
methodName
parameterJson
actualDecision
suspected
confidence
score
classification
processingTimeMs
fromSnapshotUrl
toSnapshotUrl
deltaFrameUrl
message
```

### Tests To Add

Test:

```text
dataset summary export returns valid JSON
calculation run export returns run metadata and rows
comparison input export includes expected labels and actual decisions
exports include stable ids
exports include image API links
exports mark missing/deleted files
unknown dataset id returns controlled error
unknown calculation run id returns controlled error
```

### Acceptance Criteria

* BenchChef can export dataset summary JSON.
* BenchChef can export calculation run JSON.
* BenchChef can export comparison input JSON.
* Export rows include enough data for CSV, Markdown, HTML, and chart generation.
* Exports do not require BenchChef to access SQLite directly.
* Exports do not require BenchChef to access filesystem paths directly.
* `mvn test` passes.

---

## 0.8.7 — Parameter Sweep API Preparation

### Status

Planned.

### Purpose

Prepare controlled parameter sweep execution using the existing calculation-run service.

The existing calculation-run execution endpoint remains:

```text
POST /admin/camera/delta-sets/{deltaSetId}/calculation-runs
```

The sweep API creates multiple normal calculation runs with different resolved parameters.

### Endpoints To Add

```text
POST /admin/camera/workbench/parameter-sweeps/preview
POST /admin/camera/workbench/parameter-sweeps
GET  /admin/camera/workbench/parameter-sweeps/{sweepId}
GET  /admin/camera/workbench/parameter-sweeps/{sweepId}/runs
```

### Work To Do

Implement:

```text
sweep request parsing
combination expansion
maximum combination validation
preview response
sweep execution
child calculation run creation
sweep status tracking
child run listing
```

### Preview Request

```json
{
  "datasetId": "spaghetti-dataset-001",
  "printerId": "pex01",
  "cameraJobId": 1,
  "deltaSetId": 1,
  "engineName": "JAVA_BASIC_DELTA",
  "methodName": "spaghetti-heuristic",
  "sweep": {
    "confidenceThreshold": [0.65, 0.75, 0.85],
    "parameterJson": [
      {
        "minimumChangedPixels": 8000,
        "chaoticLocalChangeScore": 0.4
      },
      {
        "minimumChangedPixels": 12000,
        "chaoticLocalChangeScore": 0.6
      }
    ]
  },
  "message": "controlled deterministic parameter sweep"
}
```

### Tests To Add

Test:

```text
preview expands combinations correctly
maximum combination count is enforced
sweep execution creates calculation runs
each child run stores resolved parameters
invalid engine is rejected
invalid delta set is rejected
invalid parameter JSON is rejected
sweep status is readable
child runs are readable
```

### Acceptance Criteria

* SpaghettiChef can preview parameter sweep combinations.
* SpaghettiChef can execute a controlled parameter sweep.
* Sweep-created runs are normal calculation runs.
* BenchChef can list child runs.
* Each child run remains reproducible through persisted parameters.
* `mvn test` passes.

---

## 0.8.8 — ML Export Preparation API

### Status

Planned.

### Purpose

Create controlled ML-ready export packages from dataset data.

This step prepares data for external training. It does not train models.

### Endpoints To Add

```text
POST /admin/camera/workbench/ml-exports
GET  /admin/camera/workbench/ml-exports
GET  /admin/camera/workbench/ml-exports/{exportId}
```

### Work To Do

Implement export creation with:

```text
source dataset id
source dataset version
selected labels
input type
train / validation / test split
label map
input image size metadata
preprocessing metadata
leakage avoidance metadata
manifest
```

Suggested export layout:

```text
ml-exports/{exportId}/
  manifest.json
  label-map.json
  preprocessing.json
  train/
  validation/
  test/
```

Initial split rule:

```text
split by camera job first
```

### Tests To Add

Test:

```text
export package is created
manifest exists
label-map exists
preprocessing metadata exists
train / validation / test split exists
same camera job is not split across train and test
volatile latest.jpg / previous.jpg / delta.jpg are not exported as historical data
unknown dataset id returns controlled error
```

### Acceptance Criteria

* ML export package can be created.
* Export manifest includes source dataset version.
* Export manifest includes label map.
* Export split avoids obvious same-camera-job leakage.
* Export uses controlled persisted source snapshots or delta frames.
* `mvn test` passes.

---

## 0.8.9 — Model Metadata API

### Status

Planned.

### Purpose

Store metadata for externally trained models.

This step records model metadata only. Runtime ML inference remains a later `0.9.x` concern.

### Endpoints To Add

```text
GET  /admin/camera/workbench/models
POST /admin/camera/workbench/models
GET  /admin/camera/workbench/models/{modelId}
```

### Model Metadata

```text
modelId
modelVersion
sourceDatasetVersion
trainingRunId
inputType
inputSize
labelMap
framework
modelFilePath
metrics
createdAt
message
```

### Tests To Add

Test:

```text
model metadata can be registered
model metadata can be listed
one model metadata entry can be read
source dataset version is persisted
metrics JSON is persisted
unknown model id returns controlled error
```

### Acceptance Criteria

* Model metadata can be stored.
* Model metadata can be read.
* Model metadata references dataset version.
* Model metadata does not activate runtime inference.
* `mvn test` passes.

---

## 0.8.10 — ML Inference Contract Dry Run

### Status

Planned.

### Purpose

Define and test the future ML inference result contract before real ML engine integration.

### Work To Do

Define an expected ML inference result shape:

```json
{
  "engineName": "ML_SPAGHETTI_MODEL",
  "engineVersion": "0.1.0",
  "modelVersion": "2026-ml-001",
  "suspected": true,
  "confidence": 0.91,
  "score": 0.91,
  "processingTimeMs": 37,
  "labelScores": {
    "normal": 0.04,
    "spaghetti": 0.91,
    "unclear": 0.05
  },
  "message": "ok"
}
```

Implement a dry-run normalization test that maps this shape to the existing comparison model:

```text
engineName
engineVersion
modelVersion
inputPath
suspected
confidence
score
processingTimeMs
expectedLabel
actualDecision
passFail
metrics
message
```

### Tests To Add

Test:

```text
ML result JSON parses correctly
label scores are preserved
suspected/confidence/score are normalized
modelVersion is preserved
result can be represented beside deterministic calculation results
invalid ML result JSON is rejected clearly
```

### Acceptance Criteria

* ML result contract is documented.
* ML dry-run result can be normalized.
* Normalized ML result can be compared with deterministic result rows.
* Real runtime ML inference remains for `0.9.x`.
* `mvn test` passes.

---

## 0.8.x Overall Acceptance Criteria

* Existing camera/admin APIs are verified and documented as the BenchChef provider contract.
* New APIs are added only for dataset summary, labels, validation, reports, exports, parameter sweeps, ML export, model metadata, and ML result contract preparation.
* BenchChef can discover datasets.
* BenchChef can inspect labels and validation issues.
* BenchChef can browse camera jobs, snapshots, delta sets, delta frames, calculation runs, and calculation results through verified existing endpoints.
* BenchChef can inspect engine settings and engine availability.
* BenchChef can request label-aware baseline reports.
* BenchChef can export report-ready JSON.
* BenchChef can prepare later ML workflows.
* Existing local dashboard behavior remains working.
* Existing local security and admin rules remain respected.
* `mvn test` passes for each implemented slice.
