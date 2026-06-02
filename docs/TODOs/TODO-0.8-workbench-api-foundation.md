# SpaghettiChef 0.8.x — Camera Job And Engine Observability Foundation

## Purpose

Prepare SpaghettiChef so external tools such as BenchChef can observe and benchmark it without turning SpaghettiChef into a monitoring product.

SpaghettiChef remains the operational product.

BenchChef measures performance from outside.

## Core Rule

SpaghettiChef must do its normal job:

```text
printer runtime
camera runtime
camera jobs
snapshot storage
delta generation
engine execution
calculation runs
local dashboard
safe REST API
````

SpaghettiChef must not become:

```text
a benchmark application
a Grafana adapter
a Prometheus-first monitoring product
a statistics platform
a dataset UI
a BenchChef replacement
```

## Identity Rule

`cameraJobId` and `deltaSetId` are scoped by `printerId`.

They are not globally unique across the whole database.

Correct identity:

```text
printerId + cameraJobId
printerId + cameraJobId + deltaSetId
printerId + calculationRunId, if calculationRunId also becomes printer-scoped later
```

Therefore new camera admin endpoints should prefer printer-scoped paths:

```text
/admin/printers/{printerId}/camera/jobs/{cameraJobId}
/admin/printers/{printerId}/camera/jobs/{cameraJobId}/delta-sets
/admin/printers/{printerId}/camera/delta-sets/{deltaSetId}
/admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/frames
/admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
```

Avoid relying on:

```text
/admin/camera/delta-sets/{deltaSetId}
```

when `deltaSetId` is not globally unique.

---

# 0.8.0 — Printer-Scoped Camera Admin API Cleanup

## Purpose

Make camera-job and delta-set APIs structurally correct before BenchChef depends on them.

## Work To Do

Review current camera admin endpoints and migrate or alias them toward printer-scoped paths.

### Keep Existing For Compatibility Where Needed

```text
GET /admin/camera/snapshot/jobs
GET /admin/camera/snapshot/jobs?printerId={printerId}
GET /admin/camera/snapshot/files/{snapshotEntryId}
POST /admin/camera/storage/{printerId}/sync
GET /admin/camera/calculation-engine-settings
PUT /admin/camera/calculation-engine-settings/{engineName}
```

### Prefer New Printer-Scoped Paths

```text
GET    /admin/printers/{printerId}/camera/jobs
GET    /admin/printers/{printerId}/camera/jobs/{cameraJobId}
DELETE /admin/printers/{printerId}/camera/jobs/{cameraJobId}
GET    /admin/printers/{printerId}/camera/jobs/{cameraJobId}/timeline
POST   /admin/printers/{printerId}/camera/jobs/{cameraJobId}/purge

GET    /admin/printers/{printerId}/camera/jobs/{cameraJobId}/delta-sets
POST   /admin/printers/{printerId}/camera/jobs/{cameraJobId}/delta-sets

GET    /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}
DELETE /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}
GET    /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/frames
GET    /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
POST   /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
```

## Acceptance Criteria

```text
camera jobs are always resolved with printerId
delta sets are always resolved with printerId
wrong printerId + cameraJobId is rejected
wrong printerId + deltaSetId is rejected
old endpoints remain only if needed for compatibility
BenchChef can use printer-scoped paths safely
mvn test passes
```

---

# 0.8.1 — Camera Job Progress And Throughput Data

## Purpose

Expose functional camera-job progress data that BenchChef can poll externally.

This is not Prometheus instrumentation inside SpaghettiChef.

## New Endpoint

```text
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/progress
```

## Response Should Include

```text
printerId
cameraJobId
state
startedAt
stoppedAt
firstCapturedAt
lastCapturedAt
captureIntervalSeconds
snapshotCount
retainedSnapshotCount
totalBytes
durationMs
snapshotsPerSecond
latestSnapshotId
latestCaptureAt
errorCount, if available
lastErrorMessage, if available
```

## Why

BenchChef can answer:

```text
How fast does SpaghettiChef create 10,000 pictures?
Does the camera job slow down over time?
Is the camera job still alive?
How many files and bytes were produced?
```

## Acceptance Criteria

```text
progress works for running jobs
progress works for stopped jobs
progress uses printerId + cameraJobId
snapshotCount is available
durationMs is available
snapshotsPerSecond is available
missing job returns controlled error
wrong printerId returns controlled error
mvn test passes
```

---

# 0.8.2 — Camera Job Timeline Verification

## Purpose

Make sure timeline data is good enough for external slowdown analysis.

## Endpoint

```text
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/timeline
```

## Response Must Include

```text
snapshot id
cameraJobId
printerId
capturedAt
fileDeleted
sizeBytes, if available
message, if available
```

## BenchChef Usage

BenchChef calculates externally:

```text
gap between captures
snapshots per minute
slowdown over time
missing capture periods
irregular capture rhythm
```

## Acceptance Criteria

```text
timeline is ordered by capturedAt
timeline belongs only to requested printerId + cameraJobId
deleted/missing files are visible
large jobs do not break the endpoint
mvn test passes
```

---

# 0.8.3 — Engine Run Timing Cleanup

## Purpose

Make existing calculation-run data good enough for performance benchmarking.

## Existing Useful Endpoint

```text
POST /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
GET  /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
GET  /admin/camera/calculation-runs/{calculationRunId}
GET  /admin/camera/calculation-runs/{calculationRunId}/results
GET  /admin/camera/calculation-runs/{calculationRunId}/compare?rightRunId={rightRunId}
```

## Work To Do

Ensure calculation runs expose:

```text
printerId
cameraJobId
deltaSetId
calculationRunId
engineName
engineVersion
methodName
parameterJson
engineStatus
executionDurationMs
resultCount
message
createdAt
finishedAt, if available
```

## Add Field If Possible

Calculation result rows should include:

```text
processingTimeMs
```

if the engine can provide or the runtime can measure it cheaply.

Do not add heavy internal monitoring just to get this.

## Acceptance Criteria

```text
run duration is visible
result count is visible
frames per second can be calculated
per-result processingTimeMs is included when available
missing timing is null, not fake zero
mvn test passes
```

---

# 0.8.4 — Engine Settings And Availability Verification

## Purpose

Expose what engines SpaghettiChef can run.

## Existing Endpoint

```text
GET /admin/camera/calculation-engine-settings
PUT /admin/camera/calculation-engine-settings/{engineName}
```

## Work To Do

Verify response includes:

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

Optional lightweight computed fields:

```text
available
availabilityMessage
```

## Acceptance Criteria

```text
BenchChef can list engines
disabled engines are visible
external CLI configuration is visible
missing executable can be reported if cheaply checkable
engineName remains stable identity
mvn test passes
```

---

# 0.8.5 — Camera Storage Summary

## Purpose

Give BenchChef a simple overview without requiring direct filesystem access.

## New Endpoint

```text
GET /admin/printers/{printerId}/camera/storage/summary
```

Optional global endpoint:

```text
GET /admin/camera/storage/summary
```

## Response Should Include

```text
printerId
storageRoot
cameraJobCount
snapshotCount
deltaSetCount
deltaFrameCount
calculationRunCount
totalSnapshotBytes
totalDeltaBytes
missingFileCount, if available
latestSnapshotAvailable
previousSnapshotAvailable
deltaPreviewAvailable
message
```

## Acceptance Criteria

```text
summary is read-only
summary does not create a dataset abstraction
summary does not require BenchChef to read filesystem paths
empty storage returns zero counts
missing files are counted if known
mvn test passes
```

---

# 0.8.6 — Optional Label Metadata

## Purpose

Support future engine accuracy and ML preparation without making labels part of the operational runtime core.

Labels are optional metadata.

SpaghettiChef can run without labels.

## Optional Endpoints

```text
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/label
PUT /admin/printers/{printerId}/camera/jobs/{cameraJobId}/label
GET /admin/printers/{printerId}/camera/jobs/label-summary
```

## Supported Labels

```text
normal
spaghetti
unclear
```

## Rule

Labels are used for:

```text
training data
accuracy checking
false positive / false negative analysis
future ML preparation
```

Labels are not required for:

```text
camera capture
delta generation
engine execution
printer operation
dashboard operation
```

## Acceptance Criteria

```text
camera job can exist without label
label is associated with printerId + cameraJobId
invalid label is rejected
label summary is available if implemented
mvn test passes
```

---

# 0.8.7 — Dataset Package Import Metadata

## Purpose

Support portable dataset packages only as import/export packaging.

Do not introduce `datasetId` as the normal runtime identity.

## Correct Concept

Runtime identity:

```text
printerId
cameraJobId
deltaSetId
calculationRunId
```

Dataset package:

```text
portable archive/folder used to import or export camera jobs
```

## Work To Do

Keep or improve:

```text
POST /admin/camera/storage/{printerId}/sync
```

Optional later:

```text
POST /admin/camera/workbench/dataset-packages/import
GET  /admin/camera/workbench/dataset-packages/imports
```

## Acceptance Criteria

```text
imported files become normal camera jobs/snapshots/deltas
imported labels become optional camera job labels
runtime APIs do not require datasetId
BenchChef does not access filesystem directly
mvn test passes
```

---

# 0.8.x Non-Goals

Do not implement inside SpaghettiChef 0.8.x:

```text
Prometheus-first internal metric system
Grafana dashboard generation
BenchChef Angular UI
benchmark runner
external OS/process monitoring
large report generator
full parameter sweep UI
ML training
model registry UI
performance supervision dashboard
portfolio UI
```

These belong to BenchChef.

---

# BenchChef Boundary

BenchChef will measure externally by calling SpaghettiChef APIs:

```text
GET /health
GET /version
GET /monitoring
GET /dashboard/{resourcePath}

GET /printers/{printerId}/camera/jobs/active
GET /admin/printers/{printerId}/camera/jobs
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/progress
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/timeline

GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/delta-sets
GET /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/frames
POST /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
GET /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
GET /admin/camera/calculation-runs/{calculationRunId}
GET /admin/camera/calculation-runs/{calculationRunId}/results
```

BenchChef calculates:

```text
HTTP latency
timeout count
error rate
snapshots per minute
snapshot slowdown
engine frames per second
average ms per frame
dashboard asset response time
backend responsiveness under load
```

External exporters handle:

```text
CPU
RAM
disk
process metrics
container metrics
```

---

# Overall Acceptance Criteria

```text
SpaghettiChef remains operational and lightweight
printer-scoped camera job identity is correct
printer-scoped delta set identity is correct
BenchChef can observe camera jobs externally
BenchChef can measure performance without reading SQLite
BenchChef can measure performance without reading internal filesystem paths
BenchChef can benchmark engine runs through stable APIs
labels are optional metadata only
dataset packages remain transport/import/export format only
no heavy monitoring system is added inside SpaghettiChef
existing dashboard behavior remains working
existing safety and security rules remain respected
mvn test passes
```

 