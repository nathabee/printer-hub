# DevOps Overview

This document summarizes the current CI pipeline, the verification scope, and the remaining delivery work for PrinterHub `0.1.x`.

Environment setup and Jenkins installation are described in:

- `install.md`

---

## Jenkins machine prerequisites

The Jenkins host must provide the tools used by the pipeline:

```bash
sudo apt update
sudo apt install openjdk-21-jdk
sudo apt install maven
sudo apt install sqlite3
sudo apt install curl
sudo apt install python3
````

Check:

```bash id="2yx0qy"
java -version
javac -version
mvn -version
sqlite3 --version
curl --version
python3 --version
```

---

## Current pipeline

The Jenkins pipeline currently performs:

```text id="svkouf"
Checkout
-> Environment check
-> Maven verify
-> Local runtime smoke test
-> Robustness smoke test
-> Prepare release bundle
-> Package release archive
-> Optional GitHub release publication
-> Archive reports and smoke artifacts
```

Core verification command:

```bash id="44euow"
mvn clean verify
```

This covers:

* compilation
* unit and component verification
* API verification
* persistence verification
* monitoring verification
* serial and simulation non-regression tests
* JaCoCo coverage generation

---

## Current DevOps phase coverage

| DevOps Phase   | Status  | Notes                                                                              |
| -------------- | ------- | ---------------------------------------------------------------------------------- |
| Checkout       | Done    | Source pulled from GitHub                                                          |
| Build          | Done    | Maven compile/package                                                              |
| Test           | Done    | JUnit verification across runtime, monitoring, persistence, API, and serial layers |
| Integrate      | Done    | Runtime components verified together                                               |
| Package        | Done    | shaded jar and release archive produced                                            |
| Runtime verify | Done    | Jenkins smoke lifecycle and robustness checks                                      |
| Release        | Partial | optional GitHub release publishing exists                                          |
| Deploy         | Not yet | no persistent staging or production deployment from Jenkins                        |

Current classification:

```text id="emk9o5"
Continuous Integration with release preparation
```

Not yet fully implemented:

```text id="aqjf8x"
continuous deployment
```

---

## What the pipeline verifies

### Maven verification

The `Verify` stage runs:

```bash id="q1n91t"
mvn -B -ntp clean verify
```

This produces:

* compiled application
* JUnit reports
* JaCoCo coverage report
* shaded runtime jar

### Local runtime smoke test

The normal lifecycle smoke stage verifies the public runtime/API surface:

```text id="lmu21e"
remove test database
start runtime
verify /health
verify initial printer list
add simulated printer
verify monitoring updates status
disable printer
verify monitoring stops
enable printer
verify monitoring resumes
update printer configuration
delete printer
inspect SQLite database
restart runtime
verify persisted printers reload
```

### Robustness smoke test

The robustness stage verifies mixed healthy and failing printers together:

```text id="lvsd03"
good simulated printer
sim-error printer
sim-timeout printer
sim-disconnected printer
API remains responsive
good printer continues updating
bad printers report ERROR
failure events are persisted with origin printer id
database event noise stays bounded
dashboard still loads
```

### HTTP robustness checks

The pipeline also verifies controlled error responses:

```text id="qgaqyj"
invalid POST body -> 400
unknown printer -> 404
wrong method -> 405
missing required field -> 400
```

---

## Generated artifacts

After a successful pipeline run, the archived artifacts include:

```text id="v9e52c"
target/surefire-reports/**
target/site/jacoco/**
target/runtime-smoke.log
target/runtime-robustness.log
target/operator-message-report.md
target/*.json
target/*.txt
release/**
*.tar.gz
```

These artifacts provide evidence for:

* test execution
* runtime/API verification
* persistence inspection
* coverage reporting
* smoke scenario review
* release bundle contents

---

## Release bundle contents

The release preparation stage collects runtime and verification outputs into a reproducible bundle.

Typical contents:

```text id="jlwmvq"
release/
├── printer-hub-<version>-all.jar
├── jacoco/
├── README.md
├── test.md
├── devops.md
├── roadmap.md
├── version.md
└── smoke/
```

The smoke folder may include:

```text id="wd2u5k"
health.json
printers-initial.json
printer-created.json
printer-after-enable.json
printers-after-restart.json
runtime-smoke.log
runtime-robustness.log
configured-printers.txt
printer-snapshots.txt
printer-events.txt
```

---

## Operator-facing evidence

The pipeline keeps an operator-facing report:

```text id="hq8lgw"
target/operator-message-report.md
```

This report is intended to summarize operationally relevant CI evidence such as:

* runtime startup and shutdown
* health and API behavior
* monitoring failure scenarios
* persistence-visible error evidence
* robustness verification outcomes

This continues the old `0.0.x` operator-message idea in the current `0.1.x` runtime form.

---

## Current maturity summary

Current state:

```text id="y1f6bj"
CI with runtime smoke verification, robustness verification,
coverage reporting, release bundling, and optional GitHub release publishing
```

What is already solid:

* centralized runtime/API verification
* simulation-based CI without real hardware
* persistence-backed smoke evidence
* robustness validation under partial printer failure
* release archive preparation

What is still future work:

* dedicated deployment stage
* staging or production promotion flow
* long-running runtime supervision outside CI
* operational packaging/install flow beyond the current archive

---

## Practical note

For normal branch verification, the pipeline is already strong enough to act as the quality gate for `0.1.x`.

The remaining DevOps expansion is mainly about:

* deployment automation
* environment promotion
* production operations
 