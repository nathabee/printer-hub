 
# ROADMAP


## Stage 1 — Interface Discovery

status : done
version : 0.0.1

Goals:

* Identify serial interface
* Read printer responses
* Document supported commands
* Verify firmware behavior

Output:

* `docs/interface-discovery.md`

---
 

### Stage2 : java to connect to printer

status : done
version : 0.0.2 and 0.0.3

 Goals : serial connection to printer

serial connection to printer
command send/receive logging
repeated status polling
basic printer initialization wait
clean disconnect
operational test against real hardware
basic handling of earlier port-access issues

version : 0.0.3
 
Goals: Logging and Repeated Command Polling

- Reuse the Java serial connection cleanly
- Send repeated G-code commands
- Read and display responses reliably
- Add simple command/response logging with timestamps
- Prepare a stable communication layer for later state modeling



### 0.0.4 — Automated testing foundation

status : done

Goals:

* extract serial communication behind an interface
* move polling logic into a testable service
* add JUnit tests
* add fake serial implementation
* test success case
* test no-response case
* test disconnect and error case
* test repeated polling behavior

  

### 0.0.5 — JaCoCo coverage

status : done

Goals:

* add JaCoCo Maven plugin
* generate local HTML coverage report
* measure current automated test coverage
* identify uncovered or low-coverage areas

Result:

* added JaCoCo Maven plugin and generated HTML coverage report
* current coverage baseline is low (12% instructions, 16% branches)
* meaningful coverage exists mainly on `PrinterPoller`
* `Main` and `SerialConnection` remain low-coverage areas for future improvement


### 0.0.6 Jenkins CI

version : 0.0.6
status : pending

**Jenkins CI**

Goals:

* automatically build the project on each commit or push
* run Maven validation and automated tests
* run JaCoCo coverage generation in CI
* archive test and coverage artifacts
* confirm that the project builds without requiring a real printer

Deliverables:

* add `Jenkinsfile`
* checkout repository in Jenkins
* run `mvn clean verify`
* publish JUnit test results
* archive JaCoCo HTML report or coverage artifacts
* document how to run the same verification locally

Expected result:

* each Jenkins build confirms that the project compiles and tests pass
* coverage report is generated in CI
* hardware-independent verification is available before later printer-state work

---

## Stage 3 — Printer State Model

version : 0.0.7

Goals:

* Define printer states
* Implement state transitions
* Validate command sequences

Example states:

```
DISCONNECTED
IDLE
PRINTING
PAUSED
ERROR
```

---

## Stage  — Remote API Layer

Goals:

* Provide REST endpoints
* Enable remote control
* Expose printer status

Example endpoints:

```
GET  /printer/status
POST /printer/start
POST /printer/pause
POST /printer/stop
```

---

### Stage   — Hardware Simulation (Arduino)

Goals:

* Simulate printer responses
* Test failure conditions
* Validate robustness

---

### Stage   —  **Optional Dockerized CI runner**

* Dockerfile for Maven/Java build
* maybe Jenkins pipeline using container agent
* no real printer required in container
 