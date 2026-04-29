# Install

This document describes the required local and CI setup for PrinterHub `0.1.x`.

It covers:

- developer machine prerequisites
- optional real-printer access
- Jenkins machine prerequisites
- Jenkins pipeline setup

---

## Developer machine prerequisites

Install the required tools:

```bash
sudo apt update
sudo apt install openjdk-21-jdk
sudo apt install maven
sudo apt install sqlite3
sudo apt install curl
sudo apt install minicom
````

Check the installation:

```bash
java -version
javac -version
mvn -version
sqlite3 --version
curl --version
```

---

## Real hardware access

Real printer access is only required when testing against an actual USB-connected printer.

Simulation mode does not need serial-port permissions.

Add the current user to the `dialout` group:

```bash
sudo usermod -aG dialout $USER
```

Then start a new login session:

```bash
logout
```

After login, verify:

```bash
groups
```

Expected result:

```text
dialout
```

If the printer is connected, the serial device is typically visible as:

```text
/dev/ttyUSB0
```

Manual checks can be done with tools such as:

```bash
minicom
```

---

## Local build and verification

Run a full local verification:

```bash
mvn clean verify
```

Expected result:

* project compiles successfully
* JUnit tests pass
* JaCoCo report is generated
* shaded runtime jar is produced

Main output locations:

```text
target/surefire-reports/
target/site/jacoco/
target/*-all.jar
```

---

## Local runtime start

Start the local runtime with simulation:

```bash
mvn exec:java \
  -Dexec.mainClass="printerhub.Main" \
  -Dprinterhub.api.port=8080 \
  -Dprinterhub.monitoring.intervalSeconds=1 \
  -Dprinterhub.databaseFile=printerhub.db
```

Expected runtime endpoints:

```text
http://localhost:8080/health
http://localhost:8080/printers
http://localhost:8080/dashboard
```

---

## Jenkins machine prerequisites

Install the required tools on the Jenkins host:

```bash
sudo apt update
sudo apt install openjdk-21-jdk
sudo apt install maven
sudo apt install sqlite3
sudo apt install curl
sudo apt install python3
```

Check the installation:

```bash
java -version
javac -version
mvn -version
sqlite3 --version
curl --version
python3 --version
```

These tools are required because the Jenkins pipeline currently performs:

* Maven build and test execution
* SQLite inspection during smoke tests
* HTTP endpoint checks with `curl`
* small JSON field extraction with `python3`

---

## Jenkins pipeline job

Create a Jenkins pipeline job.

Example:

Jenkins → New Item

Name:

```text
printerhub-develop
```

Type:

```text
Pipeline
```

Then configure:

Pipeline:

```text
Pipeline script from SCM
```

SCM:

```text
Git
```

Repository URL:

```text
https://github.com/nathabee/printer-hub.git
```

Branch specifier example:

```text
*/develop
```

Script Path:

```text
Jenkinsfile
```

Save the job.

---

## Optional GitHub token for release publishing

A GitHub token is not required for normal repository checkout over public HTTPS.

It is only needed when the pipeline should publish a GitHub release.

Add the credential in Jenkins:

Jenkins → Manage Jenkins → Credentials → Add

Type:

```text
Secret text
```

ID:

```text
github-token
```

Value:

```text
GitHub personal access token
```

Description:

```text
GitHub release token
```

---

## First Jenkins build

Run the pipeline job.

Expected result:

* repository is checked out
* `mvn clean verify` succeeds
* JUnit reports are published
* JaCoCo coverage is archived
* normal lifecycle smoke test succeeds
* robustness smoke test succeeds
* runtime and smoke artifacts are archived

Typical archived evidence includes:

```text
target/surefire-reports/**
target/site/jacoco/**
target/runtime-smoke.log
target/runtime-robustness.log
target/operator-message-report.md
```

---

## Notes

* Simulation mode is the default verification path in CI.
* Real hardware is not required for Jenkins verification.
* Serial-port permissions are only relevant for manual real-printer testing.
* SQLite is part of the runtime verification flow and is therefore required both locally and in Jenkins.
 