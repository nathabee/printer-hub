# Quickstart

This guide explains how to build and run PrinterHub locally.

It focuses only on the steps required to execute the application.

---

## Requirements

Operating system:

- Linux (tested on Ubuntu)

Required tools:

- Java 21
- Maven
- USB access permissions
- Serial device available for real mode, or simulation mode available without hardware

Optional:

- minicom (for manual serial testing)

---

## Clone Repository

```bash
git clone https://github.com/nathabee/printer-hub.git
cd printer-hub
```

---

## Build Project

Compile and run tests:

```bash
mvn clean verify
```

This step:

* compiles source code
* executes automated tests
* generates coverage reports
* validates runtime simulation

---

## Run in Simulation Mode

Use simulated serial adapter.

Safe and hardware-independent.

```bash
java -jar target/printer-hub-<version>-all.jar SIM_PORT M105 3 100 sim
```

Typical test command:

```text
M105
```

This requests temperature data.

---

## Run with Real Printer

Connect printer via USB.

Verify device path:

```bash
ls /dev/ttyUSB*
```

Expected example:

```text
/dev/ttyUSB0
```

Run:

```bash
java -jar target/printer-hub-<version>-all.jar /dev/ttyUSB0 M105 3 2000 real
```

---
## Run API Mode

API mode starts PrinterHub as a small HTTP service.

Simulation mode:

```bash
java -jar target/printer-hub-<version>-all.jar api SIM_PORT sim 18080
```

Real printer mode:

```bash
java -jar target/printer-hub-<version>-all.jar api /dev/ttyUSB0 real 18080
```

From another terminal:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/printer/status
curl -X POST http://localhost:18080/printer/poll
curl http://localhost:18080/printer/status
```

Endpoints:

```text
GET  /health
GET  /printer/status
POST /printer/poll
```

Note:

`/health` checks the API server.
`/printer/poll` checks the printer and updates the printer snapshot.


---

## Safe Test Commands

Recommended initial commands:

```text
M105   Read temperature
M114   Read position
M115   Firmware info
```

Avoid movement commands until communication stability is confirmed.

---

## Serial Permissions

If permission errors occur:

```bash
sudo usermod -aG dialout $USER
```

Then:

```bash
logout
login again
```

Verify:

```bash
groups
```

Expected:

```text
dialout
```

---

## Troubleshooting

Common issues:

### Serial port not found

Check:

```bash
ls /dev/ttyUSB*
```

Reconnect USB cable if missing.

---

### Permission denied

Ensure:

```bash
groups
```

Contains:

```text
dialout
```

---

### Printer not responding

Check:

* correct USB cable
* correct port path
* printer powered on
* firmware active

---

## Next Steps

After successful execution:

* review logs
* inspect polling behavior
* explore test results
* examine roadmap

See:

* [`roadmap.md`](roadmap.md)
* [`industrial-bio-printer-simulation.md`](industrial-bio-printer-simulation.md)
* [`devops.md`](devops.md)
 