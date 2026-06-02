## 0.1.x — Local Farm Runtime Architecture

Goal:

Restructure PrinterHub into the correct local farm runtime architecture.

The `0.1.x` line must first create the runtime backbone, then migrate existing features into it.

Target architecture:

```text
PrinterHub Java Runtime
├── Web server thread pool
│   └── handles REST API / dashboard HTTP requests
│
├── Monitoring scheduler
│   ├── printer-1 polling task
│   ├── printer-2 polling task
│   ├── printer-3 polling task
│   └── ...
│
├── Runtime state cache
│   └── latest known printer state per configured printer
│
├── Database access layer
│   └── persists snapshots, events, jobs, config
│
└── Serial communication layer
    ├── /dev/ttyUSB0
    ├── /dev/ttyUSB1
    └── SIM_PORT
```

Important rule:

```text
The API must not poll printers directly during normal dashboard/status reads.
The monitoring scheduler polls printers in the background.
The API reads the latest known state from the runtime state cache.
```

---

### 0.1.0 — Local Runtime Backbone

status: done

Goals:

* create a new branch in github develop where we remove the old src code in order to start from scratch
* introduce the final local PrinterHub runtime structure
* create the multi-threaded runtime backbone immediately
* run one Java process containing:

  * HTTP server thread pool
  * monitoring scheduler
  * runtime printer registry
  * runtime state cache
  * database access layer
  * serial communication layer
* support placeholders where implementation is not complete yet
* prepare migration of existing `0.0.x` code into the new structure

Expected components:

```text
PrinterHubRuntime
PrinterRuntimeNode
PrinterRegistry
PrinterRuntimeStateCache
PrinterMonitoringScheduler
PrinterMonitoringTask
RemoteApiServer
DatabaseInitializer
```

Expected runtime lifecycle:

```text
start database
load or register printers
create runtime registry
create state cache
start monitoring scheduler
start API server
```

Shutdown lifecycle:

```text
stop API server
stop monitoring scheduler
disconnect printers
close database resources
```

Expected result:

* PrinterHub becomes a structured local farm runtime
* API, monitoring, database, and serial communication are separated internally
* multiple printers can be monitored independently
* one failing printer does not block the whole runtime
* old `0.0.x` code can be reused selectively, but no longer controls the architecture

---



### 0.1.1 — Migrate Existing Monitoring into Runtime Tasks

status: done

Goals:

* move existing polling logic into `PrinterMonitoringTask`
* replace single-printer monitoring with per-printer monitoring tasks
* run independent polling cycles per configured printer
* isolate timeout, disconnect, and error handling per printer
* migrate simulated printer communication into the new runtime
* migrate real serial printer communication into the new runtime
* support polling commands such as `M105`, response parsing, timeout handling, and error state handling

Expected result:

* each printer has its own monitoring cycle
* background monitoring updates the runtime state cache
* monitoring failures are stored per printer
* simulated and real printer nodes can both be monitored
* the API remains responsive while monitoring runs
 

### 0.1.2 — Migrate Existing API and Dashboard onto Runtime Services

status: done

Goals:

* make REST API handlers call runtime services only
* remove printer orchestration from HTTP handlers
* expose local farm state from the runtime registry and state cache
* keep dashboard reads separate from hardware polling
* migrate dashboard printer cards
* migrate printer configuration API
* support adding, removing, enabling, disabling, and updating configured printers

Expected endpoints:

```text
GET    /health
GET    /printers
GET    /printers/{id}
GET    /printers/{id}/status
POST   /printers
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable
GET    /dashboard
```

Expected result:

* API becomes a clean facade over the local runtime
* dashboard reads current known state through the API
* printer cards are visible again
* printers can be configured through the runtime API/dashboard
* normal API reads do not trigger serial communication directly

Suggested execution order inside 0.1.2 :

#### Step A — Runtime REST API

Implement first:

GET    /health
GET    /printers
GET    /printers/{id}
GET    /printers/{id}/status
POST   /printers
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable

This must use:

PrinterRegistry
PrinterRuntimeStateCache
PrinterMonitoringScheduler

No direct polling in handlers.

#### Step B — Dashboard

Then migrate:

GET /dashboard

and dashboard printer cards.

Dashboard must call the API/read cached state. It must not trigger polling.

---

### 0.1.3 — Migrate Persistence into Runtime Stores

status: done

Goals:

* isolate SQLite access behind store/repository classes
* avoid database logic inside API or monitoring code
* persist printer configuration
* persist polling snapshots
* persist printer events and monitoring failures
* keep the database schema ready for later job persistence
* prepare later replacement or extension of persistence

Implementation steps:

#### Step A — Persist Monitoring Snapshots and Events

* migrate SQLite database setup into the new runtime persistence layer
* persist polling snapshots from background monitoring
* persist printer events for successful polls, timeouts, disconnects, and errors
* keep monitoring code independent from SQL details by using stores

Expected stores:

```text
PrinterSnapshotStore
PrinterEventStore
```

#### Step B — Persist Runtime Printer Configuration

* persist configured printer nodes
* load configured printers at runtime startup
* keep bootstrap printers only as fallback/default seed data
* support dashboard/API-created printers surviving restart
* prepare global/default runtime configuration for later monitoring rules

Expected stores:

```text
PrinterConfigurationStore
```

Expected result:

* database access becomes clean and replaceable
* runtime services no longer depend directly on SQL details
* configured printers survive restart
* monitoring history survives restart
* persistence is part of the local runtime, not part of the API layer

---
 
### 0.1.4 — Runtime Verification, Error Management, and Non-Regression

status: done

Purpose:

Validate that the new `0.1.x` runtime architecture is not only structurally clean, but also operationally reliable.

This release completes the delayed hardening work from the `0.1.x` refactoring:

* centralized and interpretable error management
* per-printer failure isolation
* runtime-safe monitoring behavior
* persistence verification
* API and dashboard non-regression
* Jenkins smoke and robustness checks
* coverage reporting restored

This is not a cosmetic test release. It is the release that proves the rewritten multi-printer runtime can safely replace the migrated `0.0.x` behavior.

Goals:

* review and harden production error management before writing tests
* define centralized runtime error patterns for monitoring, API, persistence, and serial communication
* ensure every printer failure is isolated to the affected printer node
* ensure monitoring errors are visible through state cache, API, persisted events, and logs
* ensure database failures do not crash the runtime or block API reads
* verify that disabled printers are not monitored
* verify that bad printers do not block good printers
* verify simulated and real-style printer nodes can coexist
* verify API responsiveness during background monitoring
* verify dashboard/API behavior after the runtime refactoring
* verify persistence stores for printer configuration, snapshots, and events
* add unit tests for runtime, monitoring, persistence, serial simulation, and API handlers
* restore Jenkins smoke tests for normal runtime lifecycle
* add Jenkins robustness smoke tests for failure scenarios
* restore JaCoCo coverage reporting
* add non-regression tests for migrated `0.0.x` behavior

Implementation steps:

#### Step A — Production Code Review and Error-Management Hardening

Review and harden the high-risk runtime files before writing tests:

```text
PrinterMonitoringTask
PrinterMonitoringScheduler
RemoteApiServer
PrinterSnapshotStore
PrinterEventStore
PrinterConfigurationStore
PrinterHubRuntime
PrinterRuntimeNode
PrinterRuntimeStateCache
SerialConnection
SimulatedPrinterPort
```

Expected behavior:

```text
disabled printer -> no polling
bad printer -> ERROR only for that printer
good printers -> continue updating
API handlers -> read runtime/cache only
database failure -> visible but not fatal
serial failure -> converted into printer-level error
invalid API request -> clear HTTP error response
shutdown cleanup -> does not create false operational errors
```

Error-management rules:

```text
catch errors at the correct boundary
never let one printer crash the scheduler
never hide operational failures silently
store failure cause in runtime state
persist meaningful monitoring events
return interpretable API errors
avoid duplicate/noisy events for repeated identical failures
avoid database pollution during expected shutdown
centralized message for monitoring
```

#### Step B — Runtime and Monitoring Unit Tests

Add JUnit tests for the runtime backbone:

```text
PrinterRegistryTest
PrinterRuntimeStateCacheTest
PrinterRuntimeNodeFactoryTest
PrinterHubRuntimeTest
PrinterMonitoringTaskTest
PrinterMonitoringSchedulerTest
```

Verify:

```text
printer registration
printer lookup
state cache updates
enabled/disabled behavior
multi-printer monitoring
failure isolation
scheduler start/stop behavior
monitoring task error handling
```

#### Step C — Persistence Unit Tests

Add JUnit tests for SQLite-backed stores:

```text
DatabaseInitializerTest
PrinterConfigurationStoreTest
PrinterSnapshotStoreTest
PrinterEventStoreTest
```

Verify:

```text
schema creation
printer configuration insert/update/delete/load
snapshot persistence
event persistence
database file override with -Dprinterhub.databaseFile
safe behavior on invalid or unavailable database path
```

#### Step D — API and Dashboard Unit Tests

Add API-level tests:

```text
RemoteApiServerTest
```

Verify:

```text
GET /health
GET /printers
GET /printers/{id}
GET /printers/{id}/status
POST /printers
PUT /printers/{id}
DELETE /printers/{id}
POST /printers/{id}/enable
POST /printers/{id}/disable
GET /dashboard
```

Also verify API errors:

```text
invalid JSON -> HTTP 400
unknown printer -> HTTP 404
wrong method -> HTTP 405
runtime failure -> controlled HTTP 500
```

#### Step E — Serial and Simulation Non-Regression Tests

Add or restore tests for migrated `0.0.x` behavior:

```text
SerialConnectionTest
SimulatedPrinterPortTest
```

Verify:

```text
M105 simulated response
sim-error behavior
sim-timeout behavior
sim-disconnected behavior
real-style adapter error conversion
response parsing compatibility
connection cleanup
```

#### Step F — Jenkins Normal Lifecycle Smoke Test
 
Restore Jenkins smoke verification using the public runtime/API surface.

Lifecycle:

```text
remove test database
start PrinterHub runtime
verify /health
verify initial printer list
add simulated printer
verify printer appears
verify monitoring updates status
disable printer
verify monitoring stops for that printer
enable printer
verify monitoring resumes
update printer configuration
verify updated configuration
delete printer
verify printer removed
stop runtime
inspect SQLite database
restart runtime
verify persisted printers reload
```

#### Step G — Jenkins Robustness Smoke Test

Add failure scenarios:

```text
add good simulated printer
add sim-error printer
add sim-timeout printer
add sim-disconnected printer
verify API remains responsive
verify good printer continues updating
verify bad printers report ERROR
verify events are persisted with origin printer id
verify failures do not create uncontrolled database noise
verify dashboard still loads
```

HTTP robustness checks:

```text
invalid POST body -> HTTP 400
unknown printer -> HTTP 404
wrong method -> HTTP 405
missing required field -> HTTP 400
```

#### Step H — Coverage and Release Readiness

Restore CI coverage reporting:

```text
mvn clean verify
JaCoCo report generation
JUnit report publication
coverage artifact archival
operator/runtime smoke logs archived
```

Expected result:

* centralized runtime error handling is implemented and verified
* monitoring is safe under partial printer failure
* API remains responsive while monitoring runs
* persistence stores are tested and reusable
* dashboard/API behavior is verified after the refactoring
* migrated `0.0.x` behavior has non-regression coverage
* Jenkins verifies startup, API lifecycle, monitoring, persistence, and robustness
* `0.1.x` becomes ready as the foundation for later job management and administration hardening
 


---
 


 
</details>


<details open>
  <summary>0.2.x — Local Runtime Administration and Job Management</summary>
 




