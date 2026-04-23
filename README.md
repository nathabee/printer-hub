<p align="center">
  <img src="docs/assets/media/banner-1544x500.png" alt="PrinterHub banner">
</p>

# PrinterHub

**PrinterHub** is a Java-based prototype for serial communication, polling, and verification of a 3D printer workflow.

The project explores how a printer-connected Java application can evolve from direct USB communication toward a more integrated software architecture with testing, CI, simulation, and later remote-control capabilities.

This work is based on hands-on interaction with a real **Creality Ender-3 V2 Neo** printer connected over USB serial.

---

## Project Goals

The long-term objective is to build up the software layers typically found around connected printer systems.

Current and planned areas include:

- serial communication with printer firmware
- G-code command handling
- repeated polling and response logging
- automated testing and CI verification
- printer state modeling
- remote control via REST API
- hardware simulation using Arduino
- optional containerized CI execution

---

## System Concept

Target architecture under exploration:

```text
Client Application
        |
     REST API
        |
     PrinterHub
        |
   Serial Interface
        |
   3D Printer Firmware
        |
 Motors / Sensors / Heaters
```

The current implemented scope is focused on the `PrinterHub` to serial-interface layer.

---

## Hardware Setup

Current test hardware:

Printer:

* Creality Ender-3 V2 Neo
* Firmware: Marlin (default factory firmware)

Connection:

* USB-C to USB-A cable
* Linux device path: `/dev/ttyUSB0`

Detected interface:

```text
ch341-uart converter detected
attached to ttyUSB0
```

Microcontroller / interface:

* STM32-based control board
* CH341 USB-to-UART interface

---

## Software Environment

Operating system:

* Ubuntu Linux

Current tools:

* minicom — manual serial communication testing
* Java 21 — application implementation
* Maven — build, test, verification
* Jenkins — CI automation

Planned later:

* Spring Boot — API layer
* Arduino — hardware simulation support

---

## Current Status

Version `0.0.6` is implemented
 
* [ROADMAP](docs/roadmap.md) 

Not implemented yet:

* printer state model
* REST API layer
* remote control
* hardware simulation with Arduino
* containerized CI runner
 
---

## DevOps

Basic Continuous Integration (CI) is implemented starting with version `0.0.6`.

Current pipeline capabilities:

- automatic build on commit
- Maven verification (`mvn clean verify`)
- execution of automated tests
- JaCoCo coverage generation
- archiving of test and coverage reports
- generation of operator-facing message report
- hardware-independent verification using simulated runtime

Current DevOps phase coverage:

```text
Checkout → Build → Test → Integrate → Archive Reports
```

Not yet implemented:

```text
Package → Release → Deploy → Runtime Verification Pipeline
```

Planned next step:

* extend Jenkins pipeline toward structured DevOps workflow
* add simulated smoke execution
* prepare release-ready artifact bundle

For details:

* see [`docs/devops.md`](docs/devops.md)


---

## Why This Project Matters

Modern industrial or laboratory printers are rarely isolated devices. They are usually part of a larger software environment with monitoring, validation, automation, and operational workflows.

This project focuses on the path from low-level device communication to more structured software integration, including:

* communication reliability
* testability
* CI verification
* operational message clarity
* future service integration

---

## Repository Structure

```text
printer-hub/
├── README.md
├── Jenkinsfile
├── docs/
│   ├── interface-discovery.md
│   ├── roadmap.md
│   └── version.md
├── src/
│   ├── main/
│   │   └── java/
│   └── test/
│       └── java/
└── pom.xml
```

---

## Safety Note

Real hardware communication currently focuses on safe validation commands and controlled polling behavior.

Typical safe commands:

```text
M105   (Read temperature)
M114   (Read position)
M115   (Firmware info)
```

Movement-related or operationally risky commands should only be introduced deliberately after validation of communication behavior and error handling.

---

## License

Prototype / educational development.

MIT License. See `LICENSE`.
 