# PrinterHub

**PrinterHub** is a Java-based prototype for controlling and monitoring networked 3D printers.

The project explores how embedded printers can be integrated into modern software systems, enabling remote control, telemetry monitoring, and scalable printer management — similar to architectures used in industrial and laboratory environments.

This work is based on hands-on interaction with a real **Creality Ender-3 V2 Neo** printer connected over USB serial.

---

# Project Goals

The long-term objective is to simulate the software layer typically found around connected industrial or bio-printing systems.

Key capabilities to explore:

- Serial communication with printer firmware
- G-code command handling
- Printer state monitoring
- Remote control via REST API
- Logging and telemetry storage
- Multi-printer orchestration (future)
- Hardware simulation using Arduino (future)

---

# System Concept

Typical architecture under development:

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

This reflects common industrial device-control patterns.

---

# Hardware Setup

Current test hardware:

Printer:

* Creality Ender-3 V2 Neo
* Firmware: Marlin (default factory firmware)

Connection:

* USB-C to USB-A cable
* Linux device path:
  `/dev/ttyUSB0`

Detected interface:

```
ch341-uart converter detected
attached to ttyUSB0
```

Microcontroller:

* STM32-based control board
* CH341 USB-to-UART interface

---

# Software Environment

Operating System:

* Ubuntu Linux

Initial Tools:

* `screen` (serial communication testing)
* Java (planned)
* Spring Boot (planned)
* Arduino (future simulation work)



---

# Current Status

✔ USB communication detected
✔ Serial device available (`/dev/ttyUSB0`)
⬜ First command exchange
⬜ Firmware identification
⬜ Java serial interface
⬜ REST control layer

---

# Planned Development Stages

## Stage 1 — Interface Discovery

Goals:

* Identify serial interface
* Read printer responses
* Document supported commands
* Verify firmware behavior

Output:

* `docs/interface-discovery.md`

---

## Stage 2 — Basic Communication Layer

Goals:

* Implement Java serial connection
* Send G-code commands
* Read responses
* Implement command logging

---

## Stage 3 — Printer State Model

Goals:

* Define printer states
* Implement state transitions
* Validate command sequences

Example states:

```
DISCONNECTED
IDLE
PRINTING
PAUSED
ERROR
```

---

## Stage 4 — Remote API Layer

Goals:

* Provide REST endpoints
* Enable remote control
* Expose printer status

Example endpoints:

```
GET  /printer/status
POST /printer/start
POST /printer/pause
POST /printer/stop
```

---

## Stage 5 — Hardware Simulation (Arduino)

Goals:

* Simulate printer responses
* Test failure conditions
* Validate robustness

---

# Why This Project Matters

Modern industrial printers — including medical and laboratory systems — are rarely standalone devices. They are components of larger networked environments.

This project explores:

* Device communication
* System integration
* Remote operation
* Reliability engineering

All using real hardware interaction.

---

# Repository Structure

```text
printer-hub/
│
├── README.md
├── docs/
│   └── interface-discovery.md
│
├── src/
│   └── (future Java implementation)
│
└── scripts/
    └── (future helper tools)
```

---

# Safety Note

All communication with the printer initially uses **read-only commands**.

Movement commands are avoided until communication behavior is fully verified.

Safe commands:

```
M105   (Read temperature)
M114   (Read position)
M115   (Firmware info)
```

---

# License

Prototype / educational development.
 
 