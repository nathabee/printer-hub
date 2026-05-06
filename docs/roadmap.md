# ROADMAP

This roadmap describes the progressive hardening of the printer communication system from hardware discovery to CI/CD-ready automation.


This roadmap separates the PrinterHub project into three architectural stages:

- `0.0.x` — prototype foundation
- `0.1.x` — local farm runtime architecture
- `0.2.x` — local runtime administration and job management
- `1.0.x` — central VPS multi-farm management

---

## 0.0.x — Prototype Foundation (done)

status: closed after `0.0.19`

Purpose:

Build and validate the first working foundation:

- serial communication
- simulated printer support
- polling
- REST API
- dashboard prototype
- job model prototype
- printer farm simulation
- SQLite persistence
- local runtime configuration foundation

Important note:

The `0.0.x` line is a prototype line.  
It proved the components, but it is not yet the final local farm architecture.
 
---



### 0.0.1 — Interface Discovery

status : done  
version : 0.0.1

Goals:

- identify serial interface
- read printer responses
- document supported commands
- verify firmware behavior

Output:

- `docs/interface-discovery.md`

---

### 0.0.2–0.0.3 — Java Printer Communication

status : done  
version : 0.0.2 and 0.0.3

#### 0.0.2

Goals:

- serial connection to printer
- command send/receive
- basic operational test against real hardware
- initial handling of port-access issues

#### 0.0.3

Goals:

- command send/receive logging
- repeated status polling
- basic printer initialization wait
- clean disconnect
- more stable communication flow for later extensions

---

### 0.0.4 — Automated Testing Foundation

status : done  
version : 0.0.4

Goals:

- extract serial communication behind an interface
- move polling logic into a testable service
- add JUnit tests
- add fake serial implementation
- test success case
- test no-response case
- test disconnect and error case
- test repeated polling behavior

---

### 0.0.5 — JaCoCo Coverage Baseline

status : done  
version : 0.0.5

Goals:

- add JaCoCo Maven plugin
- generate local HTML coverage report
- measure current automated test coverage
- identify uncovered or low-coverage areas

Result:

- added JaCoCo Maven plugin
- generated HTML coverage report
- established an initial coverage baseline
- identified `Main` and `SerialConnection` as key low-coverage areas

---

### 0.0.6 — Jenkins CI and Broader Automated Verification

status : done  
version : 0.0.6

Goals:

- automatically build the project on each commit or push
- run Maven verification and automated tests in Jenkins
- run JaCoCo coverage generation in CI
- archive test and coverage artifacts
- confirm that the project builds without requiring a real printer
- improve automated coverage across the main runtime paths
- add operator-facing runtime message reporting for operational review

Deliverables:

- add `Jenkinsfile`
- checkout repository in Jenkins
- run `mvn clean verify`
- publish JUnit test results
- archive JaCoCo coverage artifacts
- generate and archive:

```text
target/operator-message-report.md
```

* extend automated tests to cover four main test classes:

```text
PrinterPollerTest
SerialConnectionTest
MainIntegrationTest
MainRobustnessTest
```

* document how to run the same verification locally

Expected result:

* each Jenkins build confirms that the project compiles and tests pass
* coverage report is generated in CI
* operator-facing error scenarios are generated for review
* hardware-independent verification is available
* Jenkins provides reliable CI evidence, but not yet release preparation or deployment

---

### 0.0.7 — DevOps Pipeline and Release Automation

status : done  
version : 0.0.7


Goals:

* transform the Jenkins pipeline into a structured DevOps workflow
* validate application runtime behavior in CI using simulation
* prepare and package a reproducible release bundle
* optionally publish releases to GitHub
* preserve technical and operator-facing evidence as release artifacts

Deliverables:

Structured Jenkins pipeline stages:

```text
checkout
environment check
build and verify
simulated smoke run
prepare release bundle
package release archive
optional GitHub release publication
```

Runtime validation:

```bash
mvn exec:java \
-Dexec.mainClass="printerhub.Main" \
-Dexec.args="SIM_PORT M105 3 100 sim"
```

Release packaging:

```text
release/
├── printer-hub-<version>.jar
├── jacoco/
├── operator-message-report.md
├── README.md
├── test.md
├── devops.md
├── roadmap.md
└── version.md
```

Release archive generation:

```text
printer-hub-<version>-release.tar.gz
```

Optional GitHub publication:

```text
create GitHub release
upload release archive
generate release notes
```

Expected result:

* pipeline demonstrates full CI verification and runtime validation
* release artifacts are structured and reproducible
* CI output becomes suitable as DevOps demonstration material
* project gains a controlled release workflow
* foundation established for future CD or deployment stages

---

 
### 0.0.8 — Printer State Model

status : done  
version : 0.0.8

Goals:

- define explicit printer states
- implement state transitions during polling
- parse printer responses into a snapshot model
- expose current printer state internally

Example states:

```text
DISCONNECTED
CONNECTING
IDLE
HEATING
PRINTING
ERROR
UNKNOWN
```

Result:

* `PrinterState` added
* `PrinterSnapshot` added
* `PrinterStateTracker` added
* `PrinterPoller` updates and reports printer state
* simulated and real printer polling both show state transitions

Expected result:

* printer behavior is represented as an explicit state model
* future API and monitoring features can rely on defined snapshots
* command flow becomes easier to validate and extend

---

### 0.0.9 — Remote API Layer

status : done  
version : 0.0.9


Goals:

* add API mode to the existing Java application
* provide REST endpoints through a lightweight embedded HTTP server
* expose printer status as JSON
* prepare remote interaction without changing the CLI workflow

Example endpoints:

```text
GET  /health
GET  /printer/status
POST /printer/poll
```

Expected result:

* PrinterHub can run as a small service
* printer status is accessible through HTTP
* later dashboard and automation features can rely on stable endpoints
* project moves toward service-oriented architecture

---

  
### 0.0.10 — Continuous API Monitoring

status : done

Goals:

* keep API mode running as a monitoring service
* add background polling in simulated mode
* update printer status without requiring manual `POST /printer/poll`
* keep `/printer/status` refreshed from the latest monitoring cycle

Expected result:

* PrinterHub behaves more like a monitoring service
* printer status can update automatically while API mode is running
* later dashboard features can read live-ish status without triggering hardware access directly

---

### 0.0.11 — API Runtime and Smoke Verification

status : done

Goals:

* add automated tests for the remote API layer
* extend Jenkins API smoke verification
* verify `/health`, `/printer/status`, and `/printer/poll` in CI
* verify automatic status refresh behavior
* document API runtime validation workflow
* ensure clean startup and shutdown behavior in CI

Example local run:

```bash
mvn exec:java \
-Dexec.mainClass="printerhub.Main" \
-Dexec.args="api SIM_PORT sim 18080"
````

Example verification:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status

sleep 3

curl http://localhost:18080/printer/status
curl -X POST http://localhost:18080/printer/poll
```

Expected result:

* API server starts reliably
* `/health` always responds
* `/printer/status` updates automatically
* manual `/printer/poll` remains functional
* Jenkins validates API runtime behavior
* release artifacts include verified API usage documentation

---

### 0.0.12 — Failure Scenario Simulation

status : done

Goals:

* simulate printer errors
* simulate disconnected printers
* simulate timeout scenarios
* expose failures through API responses and printer status

Expected result:

* system can demonstrate operational failure handling
* `ERROR` and `DISCONNECTED` states become testable through the API
* project better represents real industrial operation

---

### 0.0.13 — Job Model Foundation

status : done

Goals:

* introduce a print job domain model
* define job lifecycle states
* prepare upload and execution workflows without real file upload yet

Example job states:

```text
CREATED
VALIDATED
ASSIGNED
RUNNING
COMPLETED
FAILED
CANCELLED
```

Expected result:

* print jobs become explicit domain objects
* future upload, queue, and dashboard features rely on a stable model
* project moves from polling-only logic toward job orchestration

---

### 0.0.14 — Job Upload Simulation

status : done

Goals:

* add endpoint for creating a simulated print job
* validate supported file or job type
* store job metadata in memory
* connect job lifecycle to printer state

Example endpoints:

```text
POST /jobs
GET  /jobs
GET  /jobs/{id}
```

Expected result:

* users can create a job through the API
* job metadata can be inspected remotely
* the system starts resembling a centralized printer-farm backend

---

### 0.0.15 — In-Memory Printer Farm Simulation

status : done

Goals:

* support multiple logical printers
* expose all printer states through the API
* assign jobs to a selected printer
* simulate a small printer farm without requiring multiple real printers

Example endpoints:

```text
GET  /printers
GET  /printers/{id}/status
POST /printers/{id}/poll 
POST /printers/{id}/jobs
```

Expected result:

* project no longer represents only one printer
* API can expose a printer fleet
* later UI can display several printers in one dashboard

---

### 0.0.16 — Central Monitoring Dashboard

status : done

Goals:

* build a small web UI for printer farm monitoring
* display all printers and their current states
* show active jobs and last known status
* call the REST API instead of accessing printers directly
* refresh status from the API periodically

Expected result:

* users can monitor the printer farm from a central UI
* project demonstrates the full chain from UI to printer service
* the industrial simulation becomes visible and portfolio-ready

---

### 0.0.17 — Database Persistence

status : done


Goals:

* add persistent storage for jobs, printer states, and events
* keep history across application restarts
* prepare traceability for industrial-style monitoring

Possible technology:

```text
SQLite first
PostgreSQL later
```

Expected result:

* jobs and printer events are stored persistently
* printer history becomes queryable
* the project gains a foundation for reporting and audit trails

---
 
### 0.0.18 — Job Execution Simulation and Dashboard Cleanup

status : done

Goals:

* reduce snapshot noise
* show only configured/active printers in the dashboard
* remove misleading fake printer cards when only one printer is active
* move assigned jobs from ASSIGNED to RUNNING
* simulate completion and failure
* connect job state changes to printer state
* expose active job per print


Expected result:

* assigned jobs no longer remain static
* dashboard can show an active runtime job
* printer state and job state start moving together

---
 
### 0.0.19 — Local Runtime Configuration and Dashboard Administration

status : done

Goals:

* persist local printer configuration in SQLite
* administer configured printer nodes from the dashboard
* add, update, enable, and disable printer nodes without source-code changes
* support multiple local printers with different ports
* allow real and simulated printers in the same local runtime
* show only enabled configured printers in the dashboard
* store monitoring rules in the database
* administer monitoring rules from the dashboard
* support configurable snapshot storage rules:
  * store when printer state changes
  * store when temperature difference exceeds threshold
  * store when minimum interval has passed

Example configuration:

```text
printer-1 -> /dev/ttyUSB0 real
printer-2 -> /dev/ttyUSB1 real
printer-3 -> SIM_PORT sim

snapshot.minIntervalSeconds = 30
snapshot.temperatureThreshold = 1.0
snapshot.storeOnStateChange = true
```

---
 
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
````

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
 

## 0.2.x — Local Runtime Administration and Job Management

Goal:

* configurable runtime behavior
* clearer local administration
* controlled manual printer commands
* job lifecycle support
* operational history and diagnostics
* local service-style packaging

---

### 0.2.0 — Monitoring Configuration and Dashboard Administration Basics

status: done

Goals:

* expose monitoring rules through the API
* persist monitoring intervals and thresholds
* allow runtime tuning without code changes
* improve dashboard cards for local administration use
* show whether a printer is enabled or disabled directly on the card
* distinguish more clearly between:

  * disabled printer
  * disconnected/error printer
  * real printer
  * simulated printer

Endpoints:

```text
GET /settings/monitoring
PUT /settings/monitoring
```

Expected settings:

```text
poll interval
snapshot minimum interval
temperature delta threshold
event deduplication window
error persistence behavior
```

Dashboard expectations:

```text
show enabled / disabled status on each printer card
show real / simulated mode more clearly
make disabled state visually distinct from failure state
keep printer cards limited to configured printers
```

Expected result:

* monitoring behavior becomes configurable without source changes
* runtime tuning becomes persistent
* the dashboard becomes clearer for day-to-day local administration
* operators can immediately see whether a printer is intentionally disabled or operationally failing

---
 

### 0.2.1 — Manual Command Execution API

status: done

Goals:

* allow controlled manual printer commands
* execute commands through a dedicated command service
* keep manual command execution separate from monitoring
* persist command-related events
* support diagnostics, maintenance, and operator intervention
* start with a controlled predefined command set, not unrestricted raw command entry
* support single operator-triggered actions from the dashboard

Initial command scope:

```text
M105  read temperature
M114  read current position
M115  read firmware info
```

Possible later extensions:

```text
raw command input
movement commands beyond homing
pause/resume commands
restricted admin-only commands
```

Example endpoints:

```text
POST /printers/{id}/commands
GET  /printers/{id}/events
```

Dashboard expectations:

```text
predefined command buttons for safe read/info commands
small parameter forms for controlled commands such as target temperatures
single operator-triggered actions directly from the printer card
command result feedback visible in the dashboard
recent printer events visible for diagnostics
no direct free-text command box in the first step
```

Expected result:

* controlled operator commands become possible
* diagnostics are no longer limited to background monitoring
* monitoring and command execution remain separated internally
* dashboard-based single-command operator actions are available
* command handling creates the basis for later job execution services

---


### 0.2.2 — Job Management over Runtime Architecture
 
- step A : Foundation
- step B : Dashboard


status: done

Goals:

* connect print jobs to the runtime printer registry
* add persistent job creation and storage
* assign jobs to configured printers
* track job lifecycle through persisted state
* keep job logic out of HTTP handlers
* prepare later execution orchestration without coupling it directly to the API layer
* extend runtime nodes with execution ownership
* coordinate job execution with monitoring to avoid concurrent printer access
* expose basic job operations through the REST API
* make basic job creation and execution available through the dashboard

Expected lifecycle:

```text
CREATED
QUEUED
ASSIGNED
RUNNING
COMPLETED
FAILED
CANCELLED
```

Initial semantic job scope:

```text
READ_TEMPERATURE
READ_POSITION
READ_FIRMWARE_INFO
HOME_AXES
SET_NOZZLE_TEMPERATURE
SET_BED_TEMPERATURE
SET_FAN_SPEED
TURN_FAN_OFF
```

Job model note:

```text
A job is a first-class runtime object with its own lifecycle.
In this first implementation, one job maps to one guarded semantic printer action.
Manual commands from 0.2.1 remain operator-triggered actions outside the job lifecycle.
```

Expected result:

* jobs become a first-class runtime concept
* printer administration and job handling are connected
* persistence is ready for later execution logic
* basic job creation and execution are available through API and dashboard
* the runtime architecture is ready for richer execution and audit features later

---

## 0.2.3 — Local Audit, History Views, and Controlled Job Actions

status: in progress
- step A,B : done 


### step A — Audit and history visibility

Goals:

* expose printer event history
* expose snapshot history
* expose job history
* expose error history
* show job execution command and result details in dashboard and API
* make local diagnostics easier through both API and dashboard views
* make operator-triggered job outcomes reviewable after the fact

### step B — New Dashboard UI and Controlled real-printer job workflows

Goals:

* Dashboard UI with menu, navigation, component to make it a 2 level UI
* implement controlled job actions as predefined workflows, not just single raw command sends
* support multi-step preparation, validation, execution, and result interpretation for real-printer actions
* validate printer readiness before state-changing jobs are executed
* allow required pre-sequences before the main action command is sent
* make `HOME_AXES` a controlled workflow instead of only a direct `G28` send


Dashboard as **two-level UI**, the menu is :

```text 
PrinterHub
├── Farm Home
├── Printers
├── Jobs
├── History
└── Settings
```

But once a printer is selected, its internal navigation should follow the Ender logic very closely:

```text  
Selected Printer
├── Home
├── Print
├── Prepare
├── Control
└── Info
```
 
---

## 0.2.3 — Local Audit, History Views, and Controlled Job Actions

status: in progress

* step A, B, C, D : done

### step A — Audit and history visibility

status: done

Goals:

* expose printer event history
* expose snapshot history
* expose job history
* expose error history
* show job execution command and result details in dashboard and API
* make local diagnostics easier through both API and dashboard views
* make operator-triggered job outcomes reviewable after the fact

### step B — New Dashboard UI and controlled real-printer job workflows

status: done

Goals:

* Dashboard UI with menu, navigation, component split to make it a two-level UI
* implement controlled job actions as predefined workflows, not just single raw command sends
* support multi-step preparation, validation, execution, and result interpretation for real-printer actions
* validate printer readiness before state-changing jobs are executed
* allow required pre-sequences before the main action command is sent
* make `HOME_AXES` a controlled workflow instead of only a direct `G28` send

Dashboard as two-level UI:

```text
PrinterHub
├── Farm Home
├── Printers
├── Jobs
├── History
└── Settings
```

Selected printer navigation:

```text
Selected Printer
├── Home
├── Print
├── Prepare
├── Control
└── Info
```
---

### step C — Correct execution diagnostics and classified outcomes

status: done

Goals:

* persist the actual printer response that led to success or failure
* distinguish clearly between:

  * successful completion
  * printer-reported failure
  * timeout / no response
  * communication failure
  * validation failure before command execution
  * workflow/orchestration failure
* ensure printer-reported failures are not rewritten as generic “no response” failures when a response exists
* persist raw and/or normalized diagnostics in job history
* persist workflow-step diagnostics, not only final job state
* show sent command, actual response, classified outcome, and failure detail in dashboard history and API responses
* support review and cleanup of completed or failed jobs, including deletion of related job diagnostics/history where implemented

Controlled job-action scope:

```text
HOME_AXES
SET_NOZZLE_TEMPERATURE
SET_BED_TEMPERATURE
SET_FAN_SPEED
TURN_FAN_OFF
```

Note:

```text
For state-changing actions such as SET_NOZZLE_TEMPERATURE or SET_BED_TEMPERATURE,
step C classifies whether the guarded workflow command path succeeded or failed.
It does not yet prove that the requested physical target was reached and stabilized afterward.
```

Expected result:

* the local runtime becomes easier to inspect after failures
* dashboard and API become more useful for troubleshooting
* printer behavior, operator actions, and job state changes become reviewable after the fact
* controlled real-printer job actions become more operationally useful

---

### step D — Asynchronous job execution

status : done

Goals: 

make POST /jobs/{id}/start return quickly, while the job runs in a background executor.

That step would include:

* add a bounded job executor pool
* keep one active job per printer
* return quickly from job start API
* use job state/events/diagnostics for progress
* keep dashboard behavior based on polling job state
* add tests for async start, busy printer rejection, and completed/failed background jobs


---

### step E — File-backed print jobs and richer preparation/verification workflows

status: planned

Goals:

* extend the job model so that jobs are no longer limited to one guarded semantic command
* support file-backed print jobs as a first-class runtime concept
* allow selection of an already prepared printable file stored on the PrinterHub host
* accept and validate printable file types, starting with `.gcode`
* persist print-file metadata and association with the job
* keep PrinterHub out of slicing logic:

  * no model slicing
  * no G-code editing
  * no slicer-host role in this version
* reject unsupported source formats for direct printing unless later explicitly integrated
* prepare richer controlled workflows where command acceptance and physical-effect verification are distinct concerns
* allow later workflow variants to include optional follow-up verification steps after state-changing commands


It will be done in this order:

- Add file metadata persistence.
- Add `.gcode` validation and storage/registration logic.
- Extend PrintJob so it can reference a print file.
- Add backend API for printable files.
- Add dashboard UI to select a file and create a file-backed job.
- Refactor workflow model to support richer step types.
- Keep actual G-code execution minimal or stubbed as “represented/prepared,” unless we decide Step E should really send the file to the printer.
- Add tests around file validation, persistence, job creation, and unsupported file rejection.


Job model note:

```text
A file-backed print job references an already prepared printable file.
PrinterHub does not generate or edit slice data in 0.2.x.
PrinterHub accepts an existing printable file, associates it with a job,
and later transfers or makes it available to the printer when execution starts.
```
 

Expected result:

* PrinterHub can represent a real print as a file-backed runtime job
* the Print area of the dashboard becomes tied to an actual printable artifact
* the runtime is prepared for real print activation using host-side stored files
* the job/workflow model is ready for richer verification-oriented actions beyond immediate command acceptance

---

### step F — Controlled real-printer print-start workflow

status: planned

Goals:

* implement controlled execution of file-backed print jobs
* treat print start as a multi-step workflow, not as one direct command send
* validate printer readiness before print activation
* prevent conflicting access between monitoring, manual commands, and active job execution
* support required preparation phases before print start
* support transfer and/or activation of the selected printable file as part of the job workflow
* persist execution-step history for the print-start workflow

Typical workflow scope:

```text
PRINT_FILE
├── validate printer enabled/reachable
├── validate no conflicting active job
├── validate fresh enough runtime state
├── optional prepare / homing / thermal checks
├── validate selected file
├── transfer or make file available to printer
├── start print
└── transition job to RUNNING
```

Expected result:

* a real print can be started through the runtime as a controlled workflow
* file-backed print jobs are no longer just metadata
* print activation becomes coordinated, reviewable, and safer for real hardware use

### step G — Running print supervision and operator controls

status: planned

Goals:

* supervise running real-printer print jobs after activation
* expose running-print state and progress as far as the printer/firmware allows
* support controlled pause and cancel behavior for active print jobs
* distinguish clearly between:

  * running
  * paused
  * cancelling
  * completed
  * failed
  * cancelled
* persist terminal evidence and operator-visible outcome details
* improve dashboard visibility for active print execution

Expected result:

* real print jobs are not only startable but operable
* dashboard and API can follow running print execution more meaningfully
* completion, cancellation, and failure become properly reviewable in job history

Expected result for 0.2.3 overall:

* audit and history views become useful for real diagnostics
* controlled printer-side actions become more robust and reviewable
* PrinterHub can manage a real print job based on an already prepared printable file
* the dashboard reflects both Ender-like printer logic and browser-native reviewability
* the runtime is ready for local real-printer print execution without becoming a slicer host

---

## 0.2.4 — Packaging Local Runtime

status: planned

Goals:

* package PrinterHub as a local runtime service
* document production-style jar execution outside the source tree
* document runtime config location
* document database location
* document dashboard/static asset location if relevant
* provide startup script and/or service wrapper for local installation
* document and support `systemd` usage
* define log location and working-directory expectations
* prepare deployment on a local machine connected to printers

Expected result:

```text
PrinterHub runs as a documented, repeatable local service near the printers,
not only as a manually started development jar.
```

---

## 0.2.5 — Runtime Recovery and Serial Device Robustness

status: planned

Goals:

* improve recovery after real USB disconnect/reconnect
* reduce problems caused by unstable `/dev/ttyUSB*` device names
* make real-printer administration more robust
* improve operator visibility for serial-port failures

Minor CR / anomalies:

* README banner and dashboard screenshot path currently points to `docs/assets/media-src/...`, not a final published-media location
* dashboard.js: editing a disabled printer will re-enable it unintentionally (`enabled: true` always set even on update)

Focus:

* keep automatic retry behavior for recoverable monitoring failures
* better distinguish between:

  * disconnected device
  * invalid configured port
  * temporary communication failure
* support or document use of stable serial paths such as:

```text
/dev/serial/by-id/...
```

* improve dashboard/API error clarity for real printer connection problems

Expected result:

* real printers recover more reliably after reconnect scenarios
* operators can understand whether the failure is caused by cable disconnect, changed port path, or invalid configuration
* local runtime administration becomes safer for real hardware use

---

## 0.2.6 — Print Asset Transfer and Printer File Handling Hardening

status: planned

Goals:

* harden host-side handling of printable files used by file-backed jobs
* clarify how PrinterHub transfers or exposes prepared `.gcode` files to the printer
* improve validation and error reporting around missing, unreadable, or invalid print files
* make print-file handling more reviewable in dashboard and API
* avoid ambiguous failures during print activation caused by file-path or transfer problems

Focus:

* host-side printable file registry or controlled file reference handling
* validation of file existence, readability, and allowed type
* clearer distinction between:

  * job exists but file missing
  * file invalid
  * file cannot be transferred
  * printer-side print activation failed after transfer
* persist file-related diagnostics in job execution history

Expected result:

* file-backed print jobs become safer and more predictable
* operators can understand whether a print failure is caused by printer behavior or by file-handling problems
* the runtime becomes more reliable for repeated real print activation

---

## 0.2.7 — Post-Print Review and Operational History Hardening

status: planned

Goals:

* improve reviewability after completed, failed, or cancelled print jobs
* strengthen operator visibility of final print outcome
* correlate print job lifecycle, printer events, and execution diagnostics more clearly
* make local troubleshooting easier after real print runs

Focus:

* better final job summaries
* clearer per-step execution history in dashboard
* stronger linkage between printer-side events and job-side state changes
* clearer operator-facing failure narratives for real print attempts

Expected result:

* local print operations become easier to review after the fact
* PrinterHub becomes more usable for repeated real-printer operations and troubleshooting
* audit value improves beyond raw event storage
 


---
## 1.0.x — Central VPS Multi-Farm Management

Goal:

Introduce the central platform that manages and observes multiple local PrinterHub runtimes.

Important architectural rule:

The VPS does not communicate directly with USB printers.
It communicates with local PrinterHub runtimes.

---

### 1.0.0 — Central Multi-Farm Architecture

status: future

Target architecture:

```text
Central VPS Dashboard
        |
        v
Central Backend API
        |
        v
Central Database
        |
        v
Farm Runtime Connectors
        |
        v
Local PrinterHub Runtime A
Local PrinterHub Runtime B
Local PrinterHub Runtime C
```

Goals:

* define central system boundaries
* distinguish local runtime from central platform
* define farm identity and registration

---

### 1.0.1 — Farm Registration

status: future

Goals:

* register local PrinterHub runtimes in the central platform
* assign farm IDs
* store farm metadata
* track farm online/offline status

---

### 1.0.2 — Central Farm Status Aggregation

status: future

Goals:

* collect printer summaries from multiple farms
* show central overview of all farms
* separate local printer state from central aggregated state

Expected result:

```text
Farm A: 3 printers
Farm B: 8 printers
Farm C: offline
```

---

### 1.0.3 — Central Dashboard

status: future

Goals:

* build VPS dashboard for all registered farms
* show farm-level and printer-level summaries
* link to local runtime details where appropriate

---

### 1.0.4 — Farm Synchronization Protocol

status: future

Goals:

* decide whether farms push data to VPS or VPS pulls from farms
* define snapshot payloads
* define event payloads
* define retry and offline behavior

Possible models:

```text
push model: local runtime -> central VPS
pull model: central VPS -> local runtime
hybrid model
```

---

### 1.0.5 — Central Job Dispatch Concept

status: future

Goals:

* define whether jobs can be submitted centrally
* route central jobs to a selected farm
* keep local runtime responsible for actual printer execution

Important rule:

```text
central platform requests work
local PrinterHub runtime executes work
```

---

### 1.0.6 — Security and Authentication

status: future

Goals:

* secure farm-to-VPS communication
* authenticate local runtimes
* protect central dashboard
* define access model

---

### 1.0.7 — Multi-Farm Operational History

status: future

Goals:

* store central history
* aggregate farm events
* expose fleet-wide diagnostics
* support future reporting

---
 







 
