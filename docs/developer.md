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

### `src/main/java/printerhub/serial/`

Serial adapter layer.  
Contains the real adapter, simulated runtime adapter, and adapter factory.

### `src/test/java/`

Automated tests and test fakes.  
Includes polling, serial connection, integration, and robustness tests.

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

### Run application in simulated mode

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
```

### Run application with real hardware

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="/dev/ttyUSB0 M105 3 2000 real"
```

---

## Before commit

Run:

```bash
mvn clean verify
```

If you changed runtime or hardware-related behavior, also run:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
```

Check generated outputs if needed:

* `target/site/jacoco/index.html`
* `target/operator-message-report.md`

---

## Notes

* use simulated mode for normal development when real hardware is not needed
* use real mode only when the device path is correct and the port is available
* do not duplicate setup instructions here; keep them in `install.md`
* do not duplicate test details here; keep them in `test.md`
 