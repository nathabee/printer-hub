# ROADMAP

This roadmap describes the progressive hardening of the printer communication system from hardware discovery to CI/CD-ready automation.

---

## 0.0.1 — Interface Discovery

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

## 0.0.2–0.0.3 — Java Printer Communication

status : done  
version : 0.0.2 and 0.0.3

### 0.0.2

Goals:

- serial connection to printer
- command send/receive
- basic operational test against real hardware
- initial handling of port-access issues

### 0.0.3

Goals:

- command send/receive logging
- repeated status polling
- basic printer initialization wait
- clean disconnect
- more stable communication flow for later extensions

---

## 0.0.4 — Automated Testing Foundation

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

## 0.0.5 — JaCoCo Coverage Baseline

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

## 0.0.6 — Jenkins CI and Broader Automated Verification

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

## 0.0.7 — DevOps Pipeline and Release Automation

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

 
## 0.0.8 — Printer State Model

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

## 0.0.9 — Remote API Layer

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

  
## 0.0.10 — Continuous API Monitoring

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

## 0.0.11 — API Runtime and Smoke Verification

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

## 0.0.12 — Failure Scenario Simulation

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

## 0.0.13 — Job Model Foundation

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

## 0.0.14 — Job Upload Simulation

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

## 0.0.15 — In-Memory Printer Farm Simulation

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

## 0.0.16 — Central Monitoring Dashboard

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

## 0.0.17 — Database Persistence

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
 
## 0.0.18 — Job Execution Simulation and Dashboard Cleanup

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
 
## 0.0.19 — Local Runtime Configuration and Dashboard Administration

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

---

## 0.0.20 — Local Runtime Administration API

status : planned

Goals:

* add API endpoints for printer registration and removal
* enable and disable printer nodes at runtime
* update printer mode, port, and display name
* persist printer configuration
* expose monitoring configuration through the API

Example endpoints:

```text
GET    /printers
POST   /printers
PUT    /printers/{id}
DELETE /printers/{id}
POST   /printers/{id}/enable
POST   /printers/{id}/disable
GET    /settings/monitoring
PUT    /settings/monitoring
```

Expected result:

* printers can be managed without changing source code
* local printer farm configuration becomes persistent
* monitoring rules can be changed through API calls
* the system prepares for dashboard-based administration

---

## 0.0.21 — Local Dashboard Administration

status : planned

Goals:

* add dashboard screens for local runtime administration
* create, edit, enable, and disable printer nodes from the dashboard
* configure snapshot storage rules from the dashboard
* display current local runtime configuration
* keep dashboard behavior aligned with configured printers only

Expected result:

* local users can manage printer configuration visually
* no fake printer cards are shown unless configured
* monitoring behavior can be tuned without restarting or editing code
* PrinterHub becomes a usable local printer-farm administration prototype

---

## 0.0.22 — Local Dashboard Job Control

status : planned

Goals:

* create jobs from the local dashboard
* assign jobs to selected printers
* start jobs from the dashboard
* show active, completed, failed, and waiting jobs
* display job history per printer

Expected result:

* local users can manage printer jobs from the dashboard
* job lifecycle becomes visible and controllable
* PrinterHub becomes a usable local printer-farm control prototype

---

## 0.0.23 — Cloud Synchronization Foundation

status : planned

Goals:

* prepare synchronization between local PrinterHub runtime and VPS/cloud backend
* send printer state, job state, and events to a central server
* keep local operation possible when cloud connection is unavailable
* define local-versus-remote ownership of actions
* prepare site identity for each local PrinterHub installation

Expected result:

* local PrinterHub instances can report to a central monitoring backend
* local operation remains independent from cloud availability
* the project starts moving from local dashboard to distributed monitoring

---

## 0.0.24 — Central Monitoring Backend

status : planned

Goals:

* build central backend for multiple PrinterHub sites
* collect states from several local installations
* expose site, printer, job, and event data centrally
* prepare authentication and site identity
* store synchronized monitoring data centrally

Expected result:

* one central system can monitor several local printer farms
* PrinterHub matches the industrial multi-site monitoring goal
* local and central responsibilities become clearly separated

---

## 0.0.25 — Remote Dashboard for Multi-Site Monitoring

status : planned

Goals:

* create central dashboard for VPS/cloud deployment
* show multiple sites and printer farms
* inspect local printer history remotely
* display job and event history across sites
* distinguish local dashboard from central dashboard

Expected result:

* remote supervisors can monitor several printer locations
* local dashboard and cloud dashboard have clearly separated roles
* PrinterHub demonstrates centralized industrial monitoring

---

## 0.0.26 — Remote Job Dispatch

status : planned

Goals:

* create jobs from the cloud dashboard
* dispatch jobs to selected local PrinterHub instances
* synchronize job state back to the cloud
* keep local confirmation and safety checks
* prevent unsafe remote execution without local readiness

Expected result:

* jobs can be created centrally and executed locally
* PrinterHub demonstrates distributed job orchestration
* remote control remains compatible with local safety constraints

---

## 0.0.xxx — Hardware Simulation with Arduino

status : planned

Goals:

* simulate printer responses with external hardware support
* reproduce selected printer scenarios without a full printer setup
* validate robustness under controlled hardware-like behavior

Expected result:

* realistic hardware-independent testing scenarios
* controlled reproduction of failure and timeout cases
* improved development and demonstration workflows

---

## 0.0.xxx — Optional Dockerized CI Runner

status : optional

Goals:

* add Dockerfile for Maven and Java build execution
* optionally run Jenkins pipeline using a container agent
* keep CI independent from local machine setup

Expected result:

* reproducible CI environment
* easier Jenkins portability
* stronger container-based DevOps workflow

 