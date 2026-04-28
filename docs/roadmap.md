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
````

Important rule:

```text
The API must not poll printers directly during normal dashboard/status reads.
The monitoring scheduler polls printers in the background.
The API reads the latest known state from the runtime state cache.
```

---

### 0.1.0 — Local Runtime Backbone

status: planned

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

status: planned

Goals:

* move existing polling logic into `PrinterMonitoringTask`
* replace single-printer monitoring with per-printer monitoring tasks
* run independent polling cycles per configured printer
* isolate timeout, disconnect, and error handling per printer

Expected result:

* each printer has its own monitoring cycle
* background monitoring updates the runtime state cache
* monitoring failures are stored per printer
* the API remains responsive while monitoring runs

---

### 0.1.2 — Migrate Existing API onto Runtime Services

status: planned

Goals:

* make REST API handlers call runtime services only
* remove printer orchestration from HTTP handlers
* expose local farm state from the runtime registry and state cache
* keep dashboard reads separate from hardware polling

Expected endpoints:

```text
GET /health
GET /printers
GET /printers/{id}
GET /printers/{id}/status
GET /dashboard
```

Expected result:

* API becomes a clean facade over the local runtime
* dashboard reads current known state through the API
* normal API reads do not trigger serial communication directly

---

### 0.1.3 — Migrate Persistence into Runtime Stores

status: planned

Goals:

* isolate SQLite access behind store/repository classes
* avoid database logic inside API or monitoring code
* persist snapshots, events, jobs, and printer configuration through dedicated stores
* prepare later replacement or extension of persistence

Expected stores:

```text
PrinterConfigurationStore
PrinterSnapshotStore
PrinterEventStore
PrintJobStore
```

Expected result:

* database access becomes clean and replaceable
* runtime services no longer depend directly on SQL details
* persistence is part of the local runtime, not part of the API layer

---

### 0.1.4 — Runtime Verification

status: planned

Goals:

* add tests for the local runtime backbone
* verify multi-printer monitoring
* verify API responsiveness during background monitoring
* verify failure isolation between printers
* verify simulated and real-style printer nodes can coexist

Expected result:

* the local farm runtime architecture is validated
* `0.1.x` is ready as the foundation for `0.2.x` administration and job management

 

---
## 0.2.x — Local Runtime Administration and Job Management

Goal:

Add the operational features originally planned for `0.0.20+`, but now on top of the correct `0.1.x` local runtime architecture.

---

### 0.2.0 — Local Runtime Administration API

status: planned

Goals:

* add API endpoints for printer registration and removal
* enable and disable printer nodes at runtime
* update printer mode, port, and display name
* persist printer configuration

Endpoints:

```text
GET    /printers
POST   /printers
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable
```

Expected result:

* printers can be managed without changing source code
* local printer farm configuration becomes persistent

---

### 0.2.1 — Monitoring Configuration API

status: planned

Goals:

* expose monitoring rules through the API
* persist monitoring intervals and thresholds
* allow local tuning without code changes

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
error persistence behavior
```

---

### 0.2.2 — Local Dashboard Administration

status: planned

Goals:

* add dashboard controls for printer configuration
* add enable/disable buttons
* show configured printers only
* distinguish real and simulated printers clearly

Expected result:

* the dashboard becomes a local administration UI
* no source-code change is needed to manage printers

---

### 0.2.3 — Job Management over Runtime Architecture

status: planned

Goals:

* connect print jobs to the runtime printer registry
* assign jobs to configured printers
* track job lifecycle through persisted state
* avoid job logic being coupled directly to HTTP handlers

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

---

### 0.2.4 — Manual Command Execution API

status: planned

Goals:

* allow controlled manual printer commands
* execute commands through `PrinterCommandService`
* persist command events
* keep command execution separate from monitoring

Example endpoints:

```text
POST /printers/{id}/commands
GET  /printers/{id}/events
```

---

### 0.2.5 — Local Audit and History Views

status: planned

Goals:

* expose printer event history
* expose job history
* expose error history
* make local diagnostics easier

Expected result:

* local runtime can be inspected after failures
* dashboard becomes useful for troubleshooting

---

### 0.2.6 — Packaging Local Runtime

status: planned

Goals:

* package PrinterHub as a local runtime service
* document systemd usage
* document runtime config location
* document database location
* prepare deployment on a local machine connected to printers

Expected result:

```text
PrinterHub runs as a local service near the printers.
```

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
 







 