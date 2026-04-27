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
* `src/main/java/printerhub/jobs` — print job model and job store abstraction
* `src/main/java/printerhub/farm` — printer farm model
* `src/main/java/printerhub/persistence` — SQLite persistence for jobs, printer events, printer snapshots, configured printers, and monitoring rules
* `src/main/resources/dashboard` — embedded monitoring dashboard and dashboard administration UI

Test code:

* `PrinterPollerTest` — polling workflow
* `SerialConnectionTest` — low-level serial behavior
* `MainIntegrationTest` — simulated end-to-end CLI flow
* `MainRobustnessTest` — invalid input and defensive behavior
* `RemoteApiServerTest` — API, jobs, printer farm, runtime configuration, and dashboard resources
* `SimulationProfileTest` — simulated failure profiles
* `PrintJobTest`, `PrintJobValidatorTest`, `PrintJobStoreTest` — job domain model
* `PrinterFarmStoreTest` — logical printer farm model
* `PersistentPrintJobStoreTest`, `PrinterEventStoreTest`, `PrinterSnapshotStoreTest` — SQLite persistence layer
* `RuntimeConfigurationStoreTest` — SQLite-backed local printer configuration and monitoring rules

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
simulated
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

Or use a dedicated runtime database file:

```bash
java -Dprinterhub.databaseFile=printerhub-test.db \
  -jar target/printer-hub-<version>-all.jar api SIM_PORT sim 18080
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

Expected result:

* `/printers` returns only enabled configured printers
* `/printer/status` and `/printer/poll` operate on the default enabled printer

---

## Runtime configuration API checks

Load current configuration:

```bash
curl http://localhost:18080/config/printers
curl http://localhost:18080/config/monitoring-rules
```

Add or update a simulated printer:

```bash
curl -X POST http://localhost:18080/config/printers \
  -H "Content-Type: application/json" \
  -d '{"id":"printer-2","name":"Simulated printer 2","portName":"SIM_PORT_2","mode":"sim"}'
```

Check that the printer appears in the live farm:

```bash
curl http://localhost:18080/printers
```

Disable the printer:

```bash
curl -X POST http://localhost:18080/config/printers/printer-2/disable
curl http://localhost:18080/config/printers
curl http://localhost:18080/printers
```

Expected result:

* `printer-2` remains visible in `/config/printers`
* `printer-2` disappears from `/printers`
* dashboard live printer cards show only enabled printers

Enable the printer again:

```bash
curl -X POST http://localhost:18080/config/printers/printer-2/enable
curl http://localhost:18080/printers
```

Update monitoring rules:

```bash
curl -X PUT http://localhost:18080/config/monitoring-rules \
  -H "Content-Type: application/json" \
  -d '{"snapshotOnStateChange":true,"temperatureThreshold":1.0,"minIntervalSeconds":30}'
```

Check monitoring rules again:

```bash
curl http://localhost:18080/config/monitoring-rules
```

Expected result:

* updated rules are returned
* later snapshot persistence follows the configured thresholds

---

## Printer history checks

```bash
curl http://localhost:18080/printers/printer-1/history
```

Expected result:

* recent persisted printer snapshots are returned
* newest entries appear first
* snapshots include state and response history

---

## Jobs checks

```bash
curl http://localhost:18080/jobs
curl -X POST http://localhost:18080/jobs
curl http://localhost:18080/jobs
```

Assign job to selected printer:

```bash
curl -X POST http://localhost:18080/printers/printer-1/jobs
curl http://localhost:18080/printers/printer-1/status
```

If `printer-2` is enabled:

```bash
curl -X POST http://localhost:18080/printers/printer-2/jobs
curl http://localhost:18080/printers/printer-2/status
```

Expected result:

* assigned jobs are linked to the selected printer
* background execution can move simulated jobs forward
* completed jobs clear the printer assignment

---

## Dashboard checks

Open:

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

* shows only enabled configured printers
* displays state, temperatures, update time, mode, port, and assigned job
* refreshes live printer data automatically every 3 seconds
* reads live data from `/printers`
* includes printer configuration administration
* includes monitoring rule administration

Dashboard administration check:

1. add `printer-2` with port `SIM_PORT_2` and mode `sim`
2. verify it appears in the configured printer list
3. verify it appears in the live printer cards
4. disable `printer-2`
5. verify it remains in configuration but disappears from live printers
6. enable `printer-2`
7. update monitoring rules
8. refresh and verify the saved values are still shown

---

## Persistence checks

The application creates a local SQLite database file:

```text
printerhub.db
```

A dedicated file can be selected with:

```bash
-Dprinterhub.databaseFile=printerhub-test.db
```

Check tables:

```bash
sqlite3 printerhub.db ".tables"
```

Expected tables:

```text
configured_printers
monitoring_rules
print_jobs
printer_events
printer_snapshots
```

Check configured printers:

```bash
sqlite3 printerhub.db "SELECT id, name, port_name, mode, enabled FROM configured_printers ORDER BY id;"
```

Check monitoring rules:

```bash
sqlite3 printerhub.db "SELECT snapshot_on_state_change, temperature_threshold, min_interval_seconds FROM monitoring_rules;"
```

Check persisted jobs:

```bash
sqlite3 printerhub.db "SELECT id, name, state, printer_id, created_at FROM print_jobs;"
```

Check persisted printer events:

```bash
sqlite3 printerhub.db "SELECT event_type, printer_id, job_id, message FROM printer_events ORDER BY id DESC LIMIT 10;"
```

Check persisted printer snapshots:

```bash
sqlite3 printerhub.db "SELECT printer_id, state, hotend_temperature, bed_temperature, last_response, created_at FROM printer_snapshots ORDER BY id DESC LIMIT 10;"
```

The runtime database is intentionally ignored by Git.

---

## Snapshot storage rule checks

Use a short interval for manual testing:

```bash
curl -X PUT http://localhost:18080/config/monitoring-rules \
  -H "Content-Type: application/json" \
  -d '{"snapshotOnStateChange":true,"temperatureThreshold":1.0,"minIntervalSeconds":5}'
```

Watch snapshot persistence:

```bash
watch -n 2 'sqlite3 printerhub.db "SELECT printer_id, state, hotend_temperature, bed_temperature, created_at FROM printer_snapshots ORDER BY id DESC LIMIT 5;"'
```

Expected result:

* first snapshot is stored
* state changes are stored
* temperature changes beyond the threshold are stored
* same-state snapshots are not stored faster than the configured minimum interval

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
java -Dprinterhub.databaseFile=printerhub-real.db \
  -jar target/printer-hub-<version>-all.jar api /dev/ttyUSB0 real 18080
```

Equivalent Maven command:

```bash
mvn exec:java \
  -Dprinterhub.databaseFile="printerhub-real.db" \
  -Dexec.mainClass="printerhub.Main" \
  -Dexec.args="api /dev/ttyUSB0 real 18080"
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
* dashboard shows the configured real printer
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
curl http://localhost:18080/config/printers
curl http://localhost:18080/config/monitoring-rules
curl http://localhost:18080/printers/printer-1/history
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
7. SQLite persistence for jobs, events, snapshots, configured printers, and monitoring rules
8. printer history API
9. runtime configuration API
10. dashboard monitoring and dashboard administration
11. CI evidence through Jenkins, JaCoCo, and archived reports
 
