<p align="center">
  <img src="docs/assets/media/banner-1544x500.png" alt="PrinterHub banner">
</p>

# PrinterHub

**PrinterHub** is a Java-based system-integration prototype that simulates how industrial printer farms are monitored, controlled, and automated.

The project starts with direct serial communication to a real **Creality Ender-3 V2 Neo** printer and incrementally evolves toward an industrial-style architecture including centralized monitoring, job management, REST APIs, database persistence, and multi-printer orchestration.

The Ender-3 printer serves as a physical reference device representing the lower hardware layer of a larger industrial workflow.

---

## Industrial Motivation

Modern laboratory and industrial printers — including bio-printers — are rarely isolated devices.

They operate as part of a **centralized software environment** where users:

- monitor multiple printers remotely
- upload printable jobs
- track printer state
- respond to failures
- maintain operational logs
- manage printer fleets

PrinterHub simulates this environment step by step, starting from hardware-level communication and progressing toward distributed system integration.

For the full industrial context:

- see [`docs/industrial-bio-printer-simulation.md`](docs/industrial-bio-printer-simulation.md)

---

## Target Architecture

PrinterHub evolves toward a centralized printer-farm structure.

```text
Central Monitoring UI
printer overview
job upload
live status
        |
        v
Backend REST API
job management
printer state API
        |
        v
Database
jobs
printer states
logs
history
        |
        v
Java Printer Control Service
polling
command execution
error handling
        |
        v
Serial Communication
USB / UART
        |
        v
3D Printer Firmware
Marlin
        |
        v
Motors / Sensors / Heaters
```

Current development focuses on the **Java control service and serial communication layer**, which form the foundation of the system.

---

## Current Scope

Implemented components:

* serial communication with printer firmware
* G-code command handling
* repeated polling
* structured logging foundation
* simulated serial adapter
* automated test framework
* Jenkins CI pipeline
* JaCoCo coverage reporting

Planned extensions:

* printer state model
* REST API backend
* job management layer
* database persistence
* centralized monitoring dashboard
* multi-printer simulation
* failure scenario simulation

See roadmap:

* [`docs/roadmap.md`](docs/roadmap.md)

---

## Hardware Reference Setup

Primary test hardware:

Printer:

* Creality Ender-3 V2 Neo
* Firmware: Marlin (factory default)

Connection:

* USB-C to USB-A cable
* Linux device path: `/dev/ttyUSB0`

Detected interface:

```text
ch341-uart converter detected
attached to ttyUSB0
```

---

## Quickstart

To build and run the project:

* see [`docs/quickstart.md`](docs/quickstart.md)

---

## DevOps and Testing

Continuous Integration is implemented using Jenkins.

Current CI workflow:

```text
Checkout → Build → Test → Verify → Archive Reports
```

Includes:

* Maven build verification
* automated test execution
* JaCoCo coverage reporting
* hardware-independent simulation execution

Details:

* [`docs/devops.md`](docs/devops.md)

---

## Repository Structure

```text
printer-hub/
├── README.md
├── Jenkinsfile
├── docs/
│   ├── quickstart.md
│   ├── industrial-bio-printer-simulation.md
│   ├── roadmap.md
│   ├── devops.md
│   ├── interface-discovery.md
│   └── version.md
├── src/
│   ├── main/java/
│   └── test/java/
└── pom.xml
```

---

## Why This Project Matters

Industrial printers — especially in laboratory and medical environments — depend on reliable software systems that manage communication, monitoring, automation, and traceability.

PrinterHub explores the transition from:

```text
single USB-connected printer
```

to:

```text
centralized monitored printer farm
```

This makes the project useful as:

* a system-integration learning platform
* an industrial architecture simulation
* a structured Java communication prototype
* a foundation for distributed printer control systems

---

## License

MIT License.

See:

* [`LICENSE`](LICENSE)
 
