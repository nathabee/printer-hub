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

## 0.0.7 — DevOps-Oriented Jenkins Pipeline Improvement

status : planned

Goals:

* evolve the Jenkins pipeline from basic CI toward structured DevOps workflow
* make pipeline phases explicit and clearly structured
* add hardware-independent runtime verification in CI
* improve release-preparation visibility without full deployment

Deliverables:

Split pipeline into clearer stages:

```text
checkout
environment check
build and verify
simulated smoke run
release bundle preparation
```

Add simulated runtime execution:

```bash
mvn exec:java \
-Dexec.mainClass="printerhub.Main" \
-Dexec.args="SIM_PORT M105 3 100 sim"
```

Archive extended CI evidence:

```text
JUnit reports
JaCoCo report
operator message report
packaged jar
```

Prepare delivery-ready structure:

```text
release/
├── printer-hub.jar
├── jacoco/
├── operator-message-report.md
├── README.md
└── test.md
```

Add documentation:

```text
devops.md
```

Expected result:

* pipeline demonstrates build, test, integration, and runtime validation
* CI output becomes suitable for DevOps demonstration
* release preparation becomes visible
* project progresses toward CI/CD readiness

---

## 0.0.8 — Printer State Model

status : planned

Goals:

* define printer states
* implement state transitions
* validate command sequences

Example states:

```text
DISCONNECTED
IDLE
PRINTING
PAUSED
ERROR
```

Expected result:

* printer behavior is represented as an explicit state model
* future API and monitoring features rely on defined states
* command flow becomes easier to validate and extend

---

## 0.0.9 — Remote API Layer

status : planned

Goals:

* provide REST endpoints
* expose printer status
* enable remote interaction with the printer workflow

Example endpoints:

```text
GET  /printer/status
POST /printer/start
POST /printer/pause
POST /printer/stop
```

Expected result:

* printer status and actions accessible through API
* later UI and automation features can rely on stable endpoints
* project moves toward service-oriented architecture

---

## 0.0.10 — Hardware Simulation with Arduino

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

## 0.0.11 — Optional Dockerized CI Runner

status : optional

Goals:

* add Dockerfile for Maven/Java build execution
* optionally run Jenkins pipeline using container agent
* keep CI independent from local machine setup

Expected result:

* reproducible CI environment
* easier Jenkins portability
* stronger container-based DevOps workflow
 