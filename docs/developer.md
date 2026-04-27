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
Allows real, simulated, and fake implementations.

### `src/main/java/printerhub/RemoteApiServer.java`

Lightweight embedded HTTP API server.  
Provides printer status, polling, jobs, printer farm, dashboard, and history endpoints.

In API mode, it starts a background monitoring scheduler.  
The scheduler polls the printer periodically, refreshes the latest `PrinterSnapshot`, stores selected snapshot history, and records printer events.

### `src/main/java/printerhub/jobs/`

Print job domain layer.  
Contains job identity, job type, lifecycle state, validation, job storage abstraction, and simulated job execution logic.

### `src/main/java/printerhub/farm/`

Local printer farm model.  
Contains configured printer nodes and their current runtime assignment state.

By default, the runtime exposes only the configured primary printer.  
Additional printer registration is planned for a later version.

### `src/main/java/printerhub/persistence/`

SQLite persistence layer.  
Stores print jobs, printer events, and selected printer snapshots.

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
Includes polling, serial connection, API runtime, simulation profile, integration, robustness, job, printer farm, and persistence tests.

---

## Daily commands

### Compile

```bash
mvn clean compile
```

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
mvn exec:java -Dprinterhub.databaseFile="printerhub-real.db" -Dexec.mainClass="printerhub.Main" -Dexec.args="/dev/ttyUSB0 M105 3 2000 real"
```

### Run API in simulated mode

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim 18080"
```

### Run API with real hardware

```bash
mvn exec:java -Dprinterhub.databaseFile="printerhub-real.db" -Dexec.mainClass="printerhub.Main" -Dexec.args="api /dev/ttyUSB0 real 18080"
```

---

## Database

The application creates a local SQLite database.

Default database file:

```text
printerhub.db
```

For real-printer local testing, use a separate database file:

```text
printerhub-real.db
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

Runtime database files are ignored by Git.

---

## Before commit

Run automated verification:

```bash
mvn clean verify
```

Run one simulated CLI smoke test:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
```

Expected:

* command finishes successfully
* output contains a simulated printer response
* no exception is printed

Run one real-printer CLI smoke test when the printer is connected:

```bash
mvn exec:java -Dprinterhub.databaseFile="printerhub-real.db" -Dexec.mainClass="printerhub.Main" -Dexec.args="/dev/ttyUSB0 M105 3 2000 real"
```

Expected:

* connection to `/dev/ttyUSB0` succeeds
* command `M105` is sent
* printer returns a temperature/status response
* command exits without exception

Run simulated API smoke test:

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
* `/printers` returns the configured printer list
* default runtime shows only `printer-1`
* `/jobs` returns a jobs array
* `/printers/printer-1/history` returns snapshot history
* `/dashboard` returns HTML

Run real API smoke test when the printer is connected:

```bash
mvn exec:java -Dprinterhub.databaseFile="printerhub-real.db" -Dexec.mainClass="printerhub.Main" -Dexec.args="api /dev/ttyUSB0 real 18080"
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
* `/printer/status` shows the real printer state or latest communication state
* `/printers` returns only the configured printer
* `/printers/printer-1/history` contains persisted snapshots
* `/dashboard` shows only configured printer cards

Check Git status:

```bash
git status
```

Make sure runtime database files are not staged.

---

## Notes

* use simulated mode for normal development when real hardware is not needed
* use real mode to verify that hardware communication still works
* use a separate database file for real-printer checks
* use failure simulation modes for disconnected, timeout, and printer-error behavior without real hardware
* do not commit runtime database files
* keep detailed verification workflows in `test.md`
* keep CI and release workflow details in `docs/devops.md`
 