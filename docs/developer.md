# Developer Guide

Short developer reference for everyday work.

Detailed setup and verification are documented in:

- `install.md`
- `test.md`
- `docs/devops.md`

---

## Main files

### `src/main/java/printerhub/Main.java`

Application entry point.  
Parses arguments, initializes the database schema, validates input, creates the connection or API server, and maps failures to exit codes.

### `src/main/java/printerhub/PrinterPoller.java`

Main polling workflow.  
Connects, waits for initialization, sends commands, reads responses, and disconnects safely.

### `src/main/java/printerhub/SerialConnection.java`

Real serial communication implementation.  
Handles open, send, read, and close against the printer port.

### `src/main/java/printerhub/PrinterPort.java`

Communication abstraction used by the polling layer.  
Allows real and fake implementations.

### `src/main/java/printerhub/RemoteApiServer.java`

Lightweight embedded HTTP API server.  
Provides printer status, polling, job, printer farm, dashboard, and history endpoints.

In API mode, it also starts a background monitoring scheduler.  
The scheduler polls the printer periodically, refreshes the latest `PrinterSnapshot`, stores snapshot history, and records printer events.

### `src/main/java/printerhub/jobs/`

Print job domain layer.  
Contains job identity, job type, lifecycle state, validation, and job storage abstraction.

### `src/main/java/printerhub/farm/`

Logical printer farm model.  
Contains printer nodes and in-memory fleet representation.

### `src/main/java/printerhub/persistence/`

SQLite persistence layer.  
Stores print jobs, printer events, and printer snapshots.

Main classes:

- `DatabaseConfig`
- `Database`
- `DatabaseInitializer`
- `PersistentPrintJobStore`
- `PrinterEvent`
- `PrinterEventStore`
- `PrinterSnapshotStore`

### `src/main/java/printerhub/serial/`

Serial adapter layer.  
Contains the real adapter, simulated runtime adapter, and adapter factory.

### `src/main/java/printerhub/serial/SimulationProfile.java`

Defines supported simulation profiles.  
Maps modes such as `sim-disconnected`, `sim-timeout`, and `sim-error` to deterministic simulated failure behavior.

### `src/main/resources/dashboard/`

Embedded dashboard resources.

### `src/test/java/`

Automated tests and test fakes.  
Includes polling, serial connection, API runtime, simulation profile, integration, robustness, job, and printer farm tests.

---

## Daily commands

### Compile

```bash
mvn clean compile
````

### Run tests

```bash
mvn test
```

### Run full verification

```bash
mvn clean verify
```

### Run application in simulated CLI mode

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
```

### Run application with real hardware

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="/dev/ttyUSB0 M105 3 2000 real"
```

### Run API in simulated mode

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim 18080"
```

### Run API with real hardware

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api /dev/ttyUSB0 real 18080"
```

---

## Database

The application creates a local SQLite database:

```text
printerhub.db
```

The schema is initialized automatically on startup.

Current tables:

```text
print_jobs
printer_events
printer_snapshots
```

Check database tables:

```bash
sqlite3 printerhub.db ".tables"
```

Check persisted jobs:

```bash
sqlite3 printerhub.db "SELECT id, name, state, printer_id, created_at FROM print_jobs;"
```

Check persisted events:

```bash
sqlite3 printerhub.db "SELECT event_type, printer_id, job_id, message FROM printer_events ORDER BY id DESC LIMIT 10;"
```

Check persisted snapshots:

```bash
sqlite3 printerhub.db "SELECT printer_id, state, last_response, created_at FROM printer_snapshots ORDER BY id DESC LIMIT 10;"
```

The runtime database is ignored by Git.

---

## Before commit

Run automated verification:

```bash
mvn clean verify
````

Run one simulated smoke test:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
```

Expected:

* command finishes successfully
* output contains a simulated printer response
* no exception is printed

Run one real-printer smoke test when the printer is connected:

```bash
mvn exec:java  -Dprinterhub.databaseFile="printerhub-real.db"  -Dexec.mainClass="printerhub.Main" -Dexec.args="/dev/ttyUSB0 M105 3 2000 real"
```

Expected:

* connection to `/dev/ttyUSB0` succeeds
* command `M105` is sent
* printer returns a temperature/status response
* command exits without exception

Run API smoke test:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
curl http://localhost:18080/printers
curl http://localhost:18080/jobs
curl http://localhost:18080/printers/printer-1/history
curl http://localhost:18080/dashboard
```

Expected:

* `/health` returns `UP`
* `/printer/status` returns JSON with a printer state
* `/printers` returns the printer fleet
* `/jobs` returns a jobs array
* `/printers/printer-1/history` returns snapshot history
* `/dashboard` returns HTML

Run real API smoke test when the printer is connected:

```bash
mvn exec:java  -Dprinterhub.databaseFile="printerhub-real.db" -Dexec.mainClass="printerhub.Main" -Dexec.args="api /dev/ttyUSB0 real 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
curl http://localhost:18080/printers/printer-1/history
```

Expected:

* `/health` returns `UP`
* `/printer/status` shows the real printer state or latest communication state
* `/printers/printer-1/history` contains persisted snapshots

Check Git status:

```bash
git status
```

---

## Test CLI simulation mode

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
```

---

## Test API simulation mode

Start API mode:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
curl http://localhost:18080/printers
curl http://localhost:18080/jobs
curl http://localhost:18080/printers/printer-1/history

watch -n 1 curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` updates automatically
* `/printers` returns the logical printer fleet
* `/jobs` returns persisted jobs
* `/printers/printer-1/history` returns persisted snapshot history
* `updatedAt` changes without manual `POST /printer/poll`
* state may move between `CONNECTING` and `IDLE` during background polling
* connection messages may appear repeatedly because each polling cycle connects, reads, and disconnects

Optional manual poll test:

```bash
curl -X POST http://localhost:18080/printer/poll
curl http://localhost:18080/printer/status
curl http://localhost:18080/printers/printer-1/history
```

Expected behavior:

* returns an immediate snapshot
* stores a printer snapshot
* records a printer event
* does not interfere with background monitoring

---

## Test job persistence

Start API mode, then from another terminal:

```bash
curl -X POST http://localhost:18080/jobs
curl http://localhost:18080/jobs
sqlite3 printerhub.db "SELECT id, name, state, printer_id, created_at FROM print_jobs;"
```

Restart the API and run again:

```bash
curl http://localhost:18080/jobs
```

Expected behavior:

* created jobs remain visible after restart
* SQLite contains the corresponding job rows

Assign a job to a selected printer:

```bash
curl -X POST http://localhost:18080/printers/printer-2/jobs
curl http://localhost:18080/printers/printer-2/status
sqlite3 printerhub.db "SELECT id, name, state, printer_id, created_at FROM print_jobs ORDER BY created_at DESC LIMIT 5;"
```

Expected behavior:

* assigned job is persisted with `ASSIGNED` state
* `printer_id` contains the selected printer id

---

## Test event and snapshot persistence

Check recent events:

```bash
sqlite3 printerhub.db "SELECT event_type, printer_id, job_id, message FROM printer_events ORDER BY id DESC LIMIT 10;"
```

Expected event types include:

```text
JOB_CREATED
JOB_ASSIGNED
PRINTER_POLLED
PRINTER_ERROR
PRINTER_DISCONNECTED
```

Check recent snapshots:

```bash
sqlite3 printerhub.db "SELECT printer_id, state, last_response, created_at FROM printer_snapshots ORDER BY id DESC LIMIT 10;"
```

Expected behavior:

* successful simulated polls store `IDLE` snapshots
* failure modes store error or disconnected snapshots
* newest entries appear first

---

## Test API failure simulation modes

Start one failure mode at a time.

Disconnected simulation:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim-disconnected 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
sqlite3 printerhub.db "SELECT event_type, printer_id, message FROM printer_events ORDER BY id DESC LIMIT 5;"
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` reports `DISCONNECTED`
* event history contains `PRINTER_DISCONNECTED`

Timeout simulation:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim-timeout 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
sqlite3 printerhub.db "SELECT event_type, printer_id, message FROM printer_events ORDER BY id DESC LIMIT 5;"
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` reports `ERROR`
* `lastResponse` contains timeout context
* event history contains `PRINTER_ERROR`

Printer error simulation:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim-error 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` reports `ERROR`
* `lastResponse` contains `Error: Simulated printer failure`

---

## Test API with real printer

Start API mode:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api /dev/ttyUSB0 real 18080"
```

From another terminal, with printer connected:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
curl http://localhost:18080/printers/printer-1/history

watch -n 1 curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` updates automatically
* printer snapshots are persisted
* connection messages may appear repeatedly because each polling cycle connects, reads, and disconnects

Optional unplug test:

```bash
curl -X POST http://localhost:18080/printer/poll
curl http://localhost:18080/printer/status
sqlite3 printerhub.db "SELECT event_type, printer_id, message FROM printer_events ORDER BY id DESC LIMIT 5;"
```

Expected behavior:

* `/health` stays `UP`
* `/printer/poll` fails or returns an error when the cable is removed
* `/printer/status` reflects the latest known or error state
* event history records the communication problem

---

## Notes

* use simulated mode for normal development when real hardware is not needed
* use failure simulation modes to test disconnected, timeout, and printer-error behavior without real hardware
* use real mode only when the device path is correct and the port is available
* do not commit runtime database files
* do not duplicate setup instructions here; keep them in `install.md`
* do not duplicate exhaustive test details here; keep them in `test.md`
 
