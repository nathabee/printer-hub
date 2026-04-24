# Test

## Quick verification

```bash
mvn test
mvn clean verify
mvn clean package
````

Generated artifacts:

* `target/site/jacoco/index.html`
* `target/operator-message-report.md`
* `target/api-smoke.log`
* `target/api-status-before.json`
* `target/api-status-after.json`

---

## Project test layout

Production code:

* `src/main/java/printerhub` — CLI, polling, serial communication, API server, printer state model
* `src/main/java/printerhub/serial` — real and simulated serial adapters
* `src/main/java/printerhub/jobs` — print job model and in-memory job store
* `src/main/java/printerhub/farm` — in-memory printer farm model
* `src/main/resources/dashboard` — embedded monitoring dashboard

Test code:

* `PrinterPollerTest` — polling workflow
* `SerialConnectionTest` — low-level serial behavior
* `MainIntegrationTest` — simulated end-to-end CLI flow
* `MainRobustnessTest` — invalid input and defensive behavior
* `RemoteApiServerTest` — API, jobs, printer farm, dashboard resources
* `SimulationProfileTest` — simulated failure profiles
* `PrintJobTest`, `PrintJobValidatorTest`, `PrintJobStoreTest` — job domain model
* `PrinterFarmStoreTest` — logical printer farm model

Test helpers:

* `FakePrinterPort`
* `printerhub.serial.FakeSerialPortAdapter`

---

## Runtime modes

| Mode          | Hardware | Command                                                                        |
| ------------- | -------: | ------------------------------------------------------------------------------ |
| CLI simulated |       No | `java -jar target/printer-hub-<version>-all.jar SIM_PORT M105 3 100 sim`       |
| CLI real      |      Yes | `java -jar target/printer-hub-<version>-all.jar /dev/ttyUSB0 M105 3 2000 real` |
| API simulated |       No | `java -jar target/printer-hub-<version>-all.jar api SIM_PORT sim 18080`        |
| API real      |      Yes | `java -jar target/printer-hub-<version>-all.jar api /dev/ttyUSB0 real 18080`   |

CLI arguments:

```text
<port> <command> <repeatCount> <delayMs> [mode]
```

API arguments:

```text
api <port> [mode] [apiPort]
```

Supported simulation modes include:

```text
sim
sim-disconnected
sim-timeout
sim-error
```

---

## Manual API checks

Start API mode:

```bash
java -jar target/printer-hub-<version>-all.jar api SIM_PORT sim 18080
```

Health and status:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
watch -n 1 curl http://localhost:18080/printer/status
```

Printer farm:

```bash
curl http://localhost:18080/printers
curl http://localhost:18080/printers/printer-1/status
curl -X POST http://localhost:18080/printers/printer-1/poll
```

Jobs:

```bash
curl http://localhost:18080/jobs
curl -X POST http://localhost:18080/jobs
curl http://localhost:18080/jobs
```

Assign job to selected printer:

```bash
curl -X POST http://localhost:18080/printers/printer-2/jobs
curl http://localhost:18080/printers/printer-2/status
```

Dashboard:

```text
http://localhost:18080/dashboard
```

Dashboard resource checks:

```bash
curl http://localhost:18080/dashboard
curl http://localhost:18080/dashboard/dashboard.css
curl http://localhost:18080/dashboard/dashboard.js
```

Expected dashboard behavior:

* shows 3 printer cards
* displays state, temperatures, update time, and assigned job
* refreshes automatically every 3 seconds
* reads data from the REST API only

---

## Failure simulation checks

Disconnected:

```bash
java -jar target/printer-hub-<version>-all.jar api SIM_PORT sim-disconnected 18080
curl http://localhost:18080/printer/status
```

Expected state:

```text
DISCONNECTED
```

Timeout:

```bash
java -jar target/printer-hub-<version>-all.jar api SIM_PORT sim-timeout 18080
curl http://localhost:18080/printer/status
```

Expected state:

```text
ERROR
```

Printer error:

```bash
java -jar target/printer-hub-<version>-all.jar api SIM_PORT sim-error 18080
curl http://localhost:18080/printer/status
```

Expected response contains:

```text
Error: Simulated printer failure
```

---

## Real printer checks

Start API mode with real hardware:

```bash
java -jar target/printer-hub-<version>-all.jar api /dev/ttyUSB0 real 18080
```

Check:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
watch -n 1 curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` updates automatically
* repeated connect/disconnect logs are expected
* unplugging the printer should move status toward failure or disconnected behavior

---

## Reports

Open coverage report:

```bash
xdg-open target/site/jacoco/index.html
```

Open operator message report:

```bash
xdg-open target/operator-message-report.md
```

Optional formatted JaCoCo XML:

```bash
sudo apt install libxml2-utils
xmllint --format target/site/jacoco/jacoco.xml > jacoco-pretty.xml
```

---

## Release jar test

After downloading and extracting a release archive:

```bash
cd release
java -jar printer-hub-<version>-all.jar SIM_PORT M105 3 100 sim
java -jar printer-hub-<version>-all.jar api SIM_PORT sim 18080
```

Then test:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printers
curl http://localhost:18080/dashboard
```

---

## Summary

The test strategy covers:

1. polling and serial communication
2. CLI execution
3. API runtime behavior
4. simulated failure modes
5. job model and job API
6. printer farm API
7. embedded dashboard resources
8. CI evidence through Jenkins, JaCoCo, and archived reports
 