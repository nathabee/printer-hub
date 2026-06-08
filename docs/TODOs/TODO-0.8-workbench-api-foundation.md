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
jobId
printerId
cameraId
state
startedAt
finishedAt
firstCapturedAt
lastCapturedAt
captureIntervalSeconds
snapshotCount
deltaCount
retainedSnapshotCount
totalBytes
durationMs
snapshotsPerSecond
latestSnapshotId
latestCaptureAt
errorType, if available
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

## Status

Done in the 0.8 camera admin API foundation work:

```text
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/progress
```

The endpoint resolves jobs by `printerId + cameraJobId`, returns snapshot counts, retained counts, bytes, duration, snapshots per second, latest snapshot data, and controlled not-found errors for missing or wrong-printer jobs.

Verified with `RemoteApiServerTest#cameraSnapshotAdminEndpointsExposeTimelineAndDeleteJobSnapshot` and full `mvn test`.

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
timestamp
eventType
state
message, if available
snapshotId
deltaSetId
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
timeline is ordered by timestamp
timeline belongs only to requested printerId + cameraJobId
large jobs do not break the endpoint
mvn test passes
```

## Status

Done in the 0.8 camera admin API foundation work:

```text
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/timeline
```

The endpoint verifies the camera job with `printerId + cameraJobId`, returns only events for that scoped identity, and the store orders entries by `captured_at ASC, id ASC`. Timeline rows use the BenchChef event fields `timestamp`, `eventType`, `state`, `message`, `snapshotId`, and `deltaSetId`.

Verified with `RemoteApiServerTest#cameraSnapshotAdminEndpointsExposeTimelineAndDeleteJobSnapshot` and full `mvn test`.

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

## Status

Done in the 0.8 camera admin API foundation work.

Calculation runs now expose `calculationRunId`, `executionDurationMs`, `resultCount`, and nullable `finishedAt` in addition to the existing printer, job, delta set, engine, method, status, parameter, message, and creation fields.

Calculation results now include nullable `processingTimeMs`. Batch calculation runs and the live delta pipeline measure per-result processing time cheaply around the existing engine/detection call. Existing rows without timing remain `null`.

Verified with `CameraDeltaSetServiceTest`, `CameraDeltaStoresTest`, and `RemoteApiServerTest#cameraDeltaSetAdminEndpointsGenerateAndListFrames`.

---

# 0.8.4 — Engine Settings Compatibility For BenchChef

## Purpose

Make the calculation engine settings endpoint stable enough for BenchChef to list available engines and understand what SpaghettiChef can execute.

This remains a SpaghettiChef operational API.

It is not a benchmark runner and not a monitoring subsystem.

## Existing Endpoints

```text
GET /admin/camera/calculation-engine-settings
PUT /admin/camera/calculation-engine-settings/{engineName}
```

## Work To Do

Verify and document that the response includes:

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

Optional lightweight computed fields may be added only if cheap:

```text
available
availabilityMessage
```

Do not execute heavy engine checks just to compute availability.

## Acceptance Criteria

```text
BenchChef can list engines
disabled engines are visible
external CLI configuration is visible
engineName remains the stable identity
missing executable can be reported if cheaply checkable
missing availability data is null or absent, not faked
mvn test passes
```

## Status

Done in the 0.8 camera admin API foundation work.

`GET /admin/camera/calculation-engine-settings` and `PUT /admin/camera/calculation-engine-settings/{engineName}` expose the stable persisted engine settings fields required by BenchChef: `engineName`, `adapterType`, `engineLabel`, `enabled`, default method/threshold/parameter/CLI settings, executable path, timeout, sort order, and timestamps.

The response also includes cheap computed `available` and `availabilityMessage` fields. Java engines report availability without extra work. External CLI engines are checked only for configured executable path existence, regular-file status, and executable permission; SpaghettiChef does not start or benchmark the engine to compute availability.

---

# 0.8.5 — Camera Storage Summary For BenchChef

## Purpose

Give BenchChef a simple read-only overview of camera storage without requiring BenchChef to read SQLite directly or access SpaghettiChef internal filesystem paths.

BenchChef needs summary data.

BenchChef must not become dependent on SpaghettiChef storage internals.

## New Endpoint

```text
GET /admin/printers/{printerId}/camera/storage/summary
```

## Optional Endpoint

```text
GET /admin/camera/storage/summary
```

The global endpoint is optional. The printer-scoped endpoint is the important one.

## Response Should Include

```text
printerId
storageRoot
cameraJobCount
snapshotCount
retainedSnapshotCount
deltaSetCount
deltaFrameCount
calculationRunCount
calculationResultCount
totalSnapshotBytes
totalDeltaBytes
missingFileCount
latestSnapshotAvailable
previousSnapshotAvailable
deltaPreviewAvailable
message
```

## Rules

```text
summary is read-only
summary does not create a dataset abstraction
summary does not expose raw filesystem dependency to BenchChef
summary uses printerId as scope
empty storage returns zero counts
missing files are counted when known
expensive filesystem scans are avoided unless already supported cheaply
```

## Acceptance Criteria

```text
BenchChef can display storage size and object counts
BenchChef can detect empty camera storage
BenchChef can detect missing-file situations when known
BenchChef does not need direct filesystem access
BenchChef does not need SQLite access
wrong printerId returns controlled error
mvn test passes
```

## Status

Done in the 0.8 camera admin API foundation work.

`GET /admin/printers/{printerId}/camera/storage/summary` returns a read-only printer-scoped summary with camera job, snapshot, delta set, delta frame, calculation run/result, byte, missing-file, and volatile preview availability fields.

The summary is derived from persisted camera rows and cheap file checks for known paths only. It does not expose a dataset abstraction, does not require BenchChef to read SQLite, and does not require BenchChef to inspect internal storage paths directly.

---

# 0.8.6 — BenchChef Probe Contract Verification

## Purpose

Make sure the SpaghettiChef Local REST API matches what BenchChef Local currently probes.

This subversion is not about adding monitoring inside SpaghettiChef.

It is about confirming that BenchChef can safely call SpaghettiChef through stable black-box HTTP endpoints.

## BenchChef Uses These SpaghettiChef Endpoints

```text
GET /health
GET /version
GET /monitoring
GET /dashboard/index.html

GET /printers/{printerId}/camera/jobs/active
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/progress
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/timeline
```

## Required Behavior

```text
2xx response means successful probe
non-2xx response means failed probe
timeouts are handled by BenchChef
invalid JSON on JSON endpoints is considered a BenchChef probe failure
dashboard index may return HTML
camera active job returns latestSnapshotId and latestCaptureAt when available
```

## Role Header Compatibility

SpaghettiChef Local uses:

```text
X-SpaghettiChef-Role
```

BenchChef must send this header when a role header is configured.

Do not introduce a second role header name for the same purpose.

## Acceptance Criteria

```text
BenchChef health probe works
BenchChef version probe works
BenchChef monitoring probe works
BenchChef dashboard index probe works
BenchChef camera active job probe works
BenchChef camera job progress probe works
BenchChef camera job timeline probe works
role header naming is aligned with SpaghettiChef
mvn test passes
```

## Status

Done in the 0.8 camera admin API foundation work.

`RemoteApiServerTest#benchChefProbeContractEndpointsReturnExpectedShapes` verifies the current BenchChef probe contract with black-box HTTP requests for:

```text
GET /health
GET /version
GET /monitoring
GET /dashboard/index.html
GET /printers/{printerId}/camera/jobs/active
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/progress
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/timeline
```

The verification also checks that the advertised role header is `X-SpaghettiChef-Role` and not `X-User-Role`.

---

# 0.8.7 — No Native Metrics Endpoint Decision

## Purpose

SpaghettiChef Local does not expose a native Prometheus endpoint in 0.8.x.

BenchChef can already measure SpaghettiChef externally by calling REST APIs and exposing BenchChef metrics from the BenchChef backend.

## Decision

SpaghettiChef exposes stable REST/JSON data needed by BenchChef.

BenchChef is responsible for converting those observations into Prometheus metrics, statistics, and dashboards.

## Required Boundary

```text
SpaghettiChef provides operational facts.
BenchChef turns those facts into metrics/statistics.
```

SpaghettiChef owns:

```text
health
version
monitoring
printer status
camera job progress
camera job timeline
delta sets
calculation runs
storage summary
```

BenchChef owns:

```text
latency
error rate
snapshots per minute
slowdown
frames per second
average processing time
Prometheus /metrics format
Grafana dashboards
```

## Do Not Implement Inside SpaghettiChef

```text
Prometheus /metrics endpoint
Prometheus text exposition format
Grafana dashboard generation
performance statistics aggregation
benchmark result storage
```

## Prometheus Configuration Rule

```text
Prometheus should scrape BenchChef backend /metrics.
Prometheus should not scrape SpaghettiChef Local /metrics.
SpaghettiChef returning 404 for /metrics is expected in 0.8.x.
```

If Prometheus reports this target as down:

```text
http://host.docker.internal:18080/metrics
```

then the scrape job is configured against the wrong service. Remove the SpaghettiChef scrape target or point Prometheus at the BenchChef backend metrics endpoint.

## Acceptance Criteria

```text
SpaghettiChef may return 404 for /metrics
BenchChef documentation says SpaghettiChef /metrics is not part of the 0.8.x contract
Prometheus does not include a required SpaghettiChef scrape job
BenchChef backend /metrics remains the metrics endpoint for current dashboards
BenchChef derives metrics from SpaghettiChef REST/JSON probes
mvn test passes
```

---

# 0.8.8 — REST API Documentation Alignment

## Purpose

Make the SpaghettiChef REST API document match the actual implemented local API and the BenchChef probe contract.

The REST API document must describe what exists, not what is only planned.

## Work To Do

Update the REST API document so that it clearly marks endpoints as:

```text
implemented
optional
planned
compatibility
cancelled
```

## Must Be Documented As Implemented If Present

```text
GET /health
GET /version
GET /monitoring
GET /dashboard/index.html

GET /printers/{printerId}/camera/jobs/active

GET /admin/printers/{printerId}/camera/jobs
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/progress
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/timeline

GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/delta-sets
POST /admin/printers/{printerId}/camera/jobs/{cameraJobId}/delta-sets

GET /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}
GET /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/frames
GET /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
POST /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs

GET /admin/camera/calculation-engine-settings
PUT /admin/camera/calculation-engine-settings/{engineName}
GET /admin/camera/calculation-runs/{calculationRunId}
GET /admin/camera/calculation-runs/{calculationRunId}/results
GET /admin/camera/calculation-runs/{calculationRunId}/trace
GET /admin/camera/calculation-runs/{calculationRunId}/compare
```

## Must Be Documented As Not Implemented In 0.8.x

```text
GET /metrics
```

## Must Be Documented As Planned Until Implemented

```text
GET /admin/printers/{printerId}/camera/storage/summary
GET /admin/camera/storage/summary
```

## Must Remain Cancelled

```text
label metadata endpoints
dataset package import metadata endpoints
```

## Acceptance Criteria

```text
REST API documentation matches implemented endpoints
BenchChef API expectation document matches SpaghettiChef REST API documentation
SpaghettiChef /metrics non-goal is clear
role header name is consistent
cancelled label and dataset sections do not appear as active work
mvn test passes
```

---

# 0.8.x Non-Goals

Do not implement inside SpaghettiChef 0.8.x:

```text
Grafana dashboard generation
BenchChef Angular UI
BenchChef Django API
benchmark runner
external OS/process monitoring
large report generator
full parameter sweep UI
ML training
model registry UI
performance supervision dashboard
Prometheus /metrics endpoint
Prometheus text exposition format
performance statistics aggregation
benchmark result storage
portfolio UI
central BenchChef synchronization
central BenchChef database
central BenchChef dashboard
```

These belong to BenchChef.

SpaghettiChef 0.8.x may return 404 for `/metrics`. That is expected. BenchChef and Prometheus must not treat SpaghettiChef `/metrics` as a required target.

---

# BenchChef Boundary

BenchChef measures SpaghettiChef externally.

BenchChef Local may call:

```text
GET /health
GET /version
GET /monitoring
GET /dashboard/index.html

GET /printers/{printerId}/camera/jobs/active
GET /admin/printers/{printerId}/camera/jobs
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/progress
GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/timeline

GET /admin/printers/{printerId}/camera/jobs/{cameraJobId}/delta-sets
GET /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/frames
POST /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
GET /admin/printers/{printerId}/camera/delta-sets/{deltaSetId}/calculation-runs
GET /admin/camera/calculation-runs/{calculationRunId}
GET /admin/camera/calculation-runs/{calculationRunId}/results
GET /admin/camera/calculation-runs/{calculationRunId}/trace
GET /admin/camera/calculation-runs/{calculationRunId}/compare
GET /admin/camera/calculation-engine-settings
```

BenchChef calculates externally:

```text
HTTP latency
timeout count
error rate
dashboard asset response time
backend responsiveness under load
camera active-job polling rhythm
snapshots per minute
snapshot slowdown
engine frames per second
average ms per frame
calculation run duration
calculation result processing time
storage object counts when summary endpoint is available
```

External exporters handle:

```text
CPU
RAM
disk
process metrics
container metrics
```

Prometheus integration for the current architecture:

```text
Prometheus scrapes BenchChef backend /metrics
BenchChef backend exposes metrics derived from stored probe samples
SpaghettiChef /metrics is not part of the 0.8.x contract
Prometheus must not scrape SpaghettiChef /metrics for the current local BenchChef dashboards
```

---

# Overall Acceptance Criteria

```text
SpaghettiChef remains operational and lightweight
printer-scoped camera job identity is correct
printer-scoped delta set identity is correct
BenchChef can observe SpaghettiChef through stable REST probes
BenchChef can measure performance without reading SQLite
BenchChef can measure performance without reading internal filesystem paths
BenchChef can benchmark engine runs through stable APIs
engine settings are visible to BenchChef
camera storage summary is available or clearly documented as planned
native SpaghettiChef /metrics is clearly documented as not part of 0.8.x
role header naming is aligned
labels remain cancelled
dataset package import metadata remains cancelled
no BenchChef UI/backend responsibilities are added to SpaghettiChef
existing dashboard behavior remains working
existing safety and security rules remain respected
mvn test passes
```
