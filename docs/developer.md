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
Parses arguments, validates input, creates the connection, and maps failures to exit codes.

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
Provides `/health`, `/printer/status`, and `/printer/poll`.

In API mode, it also starts a background monitoring scheduler.  
The scheduler polls the printer periodically and refreshes the latest `PrinterSnapshot`, so `/printer/status` can update without requiring manual `POST /printer/poll`.

### `src/main/java/printerhub/serial/`

Serial adapter layer.  
Contains the real adapter, simulated runtime adapter, and adapter factory.

### `src/main/java/printerhub/serial/SimulationProfile.java`

Defines supported simulation profiles.  
Maps modes such as `sim-disconnected`, `sim-timeout`, and `sim-error` to deterministic simulated failure behavior.

### `src/test/java/`
 
Automated tests and test fakes.  
Includes polling, serial connection, API runtime, simulation profile, integration, and robustness tests.
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

### Run application in simulated mode

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
```

### Run application with real hardware

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="/dev/ttyUSB0 M105 3 2000 real"
```

## Before commit

Run:

```bash
mvn clean verify
```

If runtime or hardware-related behavior changed, also run the checks below.

### Test CLI simulation mode

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
```

### Test API simulation mode

Start API mode:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status

watch -n 1 curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` updates automatically
* `updatedAt` changes without manual `POST /printer/poll`
* state may move between `CONNECTING` and `IDLE` during background polling
* connection messages may appear repeatedly because each polling cycle connects, reads, and disconnects

Optional manual poll test:

```bash
curl -X POST http://localhost:18080/printer/poll
curl http://localhost:18080/printer/status
```

Expected behavior:

* returns an immediate snapshot
* does not interfere with background monitoring

### Test API failure simulation modes

Start one failure mode at a time.

Disconnected simulation:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim-disconnected 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` reports `DISCONNECTED`

Timeout simulation:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api SIM_PORT sim-timeout 18080"
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` reports `ERROR`
* `lastResponse` contains timeout context

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

### Test API with real printer

Start API mode:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="api /dev/ttyUSB0 real 18080"
```

From another terminal, with printer connected:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status

watch -n 1 curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/status` updates automatically
* connection messages may appear repeatedly because each polling cycle connects, reads, and disconnects

Optional unplug test:

```bash
curl -X POST http://localhost:18080/printer/poll
curl http://localhost:18080/printer/status
```

Expected behavior:

* `/health` stays `UP`
* `/printer/poll` fails or returns an error when the cable is removed
* `/printer/status` reflects the latest known or error state

---

## Notes

* use simulated mode for normal development when real hardware is not needed
* use failure simulation modes to test disconnected, timeout, and printer-error behavior without real hardware
* use real mode only when the device path is correct and the port is available
* do not duplicate setup instructions here; keep them in `install.md`
* do not duplicate test details here; keep them in `test.md`
 