# Quickstart

This guide explains how to run SpaghettiChef from an expert package or build it
locally from source.

It focuses only on the shortest path to:

- run a prebuilt package without recompilation
- optionally build the project
- start the local runtime
- start the central read-only VPS viewer
- add a simulated printer
- register a farm with the central viewer by API
- verify monitoring through the API and dashboard

---

## Requirements

Operating system:

- Linux for the Linux package
- Windows for the Windows package
- Linux tested on Ubuntu for development and Jenkins

Required tools:

- Java 21

Required only when building from source:

- Maven
- SQLite
- curl

Optional:

- minicom for manual serial checks on real hardware

Real hardware is not required for normal local verification.

---

## Run From Package

Use this path when you downloaded a Jenkins/GitHub artifact and do not want to
recompile.

Linux:

```bash
tar -xzf spaghetti-chef-<version>-linux.tar.gz
cd linux
./spaghettichef.sh 18080
```

Windows:

```bat
rem extract spaghetti-chef-<version>-windows.zip first
spaghettichef.bat 18080
```

Then open:

```text
http://localhost:18080/dashboard
```

The packages require Java 21 to be installed already.

---

## Clone repository

```bash
git clone https://github.com/nathabee/spaghetti-chef.git
cd spaghetti-chef
```

---

## Build project

Run:

```bash
mvn clean verify
```

This step:

* compiles the application
* runs automated tests
* generates JaCoCo coverage
* builds the shaded runtime jar

Main outputs:

```text
target/surefire-reports/
target/site/jacoco/
target/spaghetti-chef-<version>-all.jar
target/spaghetti-chef-<version>-central-vps.jar
```

---

## Run The Built Jar

After `mvn clean verify`, you can run the shaded jar directly.

Linux:

```bash
java -Dspaghettichef.databaseFile=spaghettichef.db -Dspaghettichef.api.port=18080 -jar target/spaghetti-chef-<version>-all.jar
```

Windows:

```bat
java -Dspaghettichef.databaseFile=spaghettichef.db -Dspaghettichef.api.port=18080 -jar target\spaghetti-chef-<version>-all.jar
```

---

## Start The Local Farm Runtime

For development, Maven can start SpaghettiChef directly:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.local.LocalMain" \
  -Dspaghettichef.api.port=18080 \
  -Dspaghettichef.monitoring.intervalSeconds=1 \
  -Dspaghettichef.databaseFile=spaghettichef.db \
  exec:java
```

Expected startup output:

```text
[SpaghettiChef] Database initialized: spaghettichef.db
[SpaghettiChef] API server started on port 18080
[SpaghettiChef] Local runtime started
[SpaghettiChef] Health:   http://localhost:18080/health
[SpaghettiChef] Printers: http://localhost:18080/printers
```

Open the local farm dashboard:

```text
http://localhost:18080/dashboard
```

Important:

```text
Use spaghettichef.databaseFile.
Do not use spaghetti.databaseFile.
```

---

## Start The Central VPS Viewer Locally

Use a second terminal and a different port:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.central.CentralMain" \
  -Dspaghettichef.api.port=18180 \
  -Dspaghettichef.central.databaseFile=spaghettichef-central.db \
  exec:java
```

Then open:

```text
http://localhost:18180/central-dashboard
```

Central mode can also be started through the normal main class:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.mode=central \
  -Dspaghettichef.api.port=18180 \
  -Dspaghettichef.central.databaseFile=spaghettichef-central.db \
  exec:java
```

The central database is separate from the local farm database.

---

## Register A Farm With Central

In 1.0.0, automatic local-to-central push is not implemented yet. To test the
central API locally, simulate the outbound farm push with `curl`.

Register a farm:

```bash
curl -s -X POST http://localhost:18180/api/central/farms/register \
  -H "Content-Type: application/json" \
  -H "X-SpaghettiChef-Registration-Token: <token-if-configured>" \
  -d '{
    "runtimeInstanceId": "local-dev-runtime-001",
    "farmName": "Local Dev Farm",
    "runtimeVersion": "1.0.0",
    "hostname": "local-dev"
  }'
```

The registration-token header is only required when central was started with
`CENTRAL_REGISTRATION_TOKEN` or
`-Dspaghettichef.central.registrationToken=...`.

Save the returned `farmId` and `farmSecret`, then send a heartbeat:

```bash
curl -s -X POST http://localhost:18180/api/central/farms/<farmId>/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "runtimeInstanceId": "local-dev-runtime-001",
    "farmSecret": "<farmSecret>",
    "runtimeVersion": "1.0.0",
    "status": "ONLINE",
    "printerCount": 1,
    "cameraCount": 0,
    "activePrintCount": 0,
    "warningCount": 0,
    "errorCount": 0,
    "spaghettiAlertCount": 0,
    "message": "manual local test"
  }'
```

Check central overview:

```bash
curl -s http://localhost:18180/api/central/farms/overview
```

Check one farm:

```bash
curl -s http://localhost:18180/api/central/farms/<farmId>
```

Push a sanitized printer/camera structure snapshot:

```bash
curl -s -X POST http://localhost:18180/api/central/farms/<farmId>/structure \
  -H "Content-Type: application/json" \
  -d '{
    "runtimeInstanceId": "local-dev-runtime-001",
    "farmSecret": "<farmSecret>",
    "generatedAt": "2026-05-31T10:30:00Z",
    "printers": [
      {
        "printerId": "p1",
        "displayName": "Local Dev Printer",
        "enabled": true,
        "status": "IDLE"
      }
    ],
    "cameras": [
      {
        "cameraId": "cam1",
        "displayName": "Front Camera",
        "printerId": "p1",
        "enabled": true
      }
    ]
  }'
```

Read it back:

```bash
curl -s http://localhost:18180/api/central/farms/<farmId>/structure
```

---

## Verify health

From another terminal:

```bash
curl http://localhost:18080/health
```

Expected result:

```json
{"status":"ok"}
```

---

## Verify initial printer list

```bash
curl http://localhost:18080/printers
```

Expected result:

```json
{"printers":[]}
```

---

## Add a simulated printer

Create one simulated printer:

```bash
curl -X POST http://localhost:18080/printers \
  -H "Content-Type: application/json" \
  -d '{
    "id": "printer-1",
    "displayName": "Local Simulated Printer",
    "portName": "SIM_PORT_1",
    "mode": "simulated",
    "enabled": true
  }'
```

Expected result:

* printer is registered
* runtime starts monitoring it
* first response may still show an early state before the first full poll finishes

---

## Check printer list again

```bash
curl http://localhost:18080/printers
```

After a short delay, expected fields should include:

* `id`
* `displayName`
* `portName`
* `mode`
* `enabled`
* `state`
* `hotendTemperature`
* `bedTemperature`
* `updatedAt`

Typical simulated state after monitoring:

```json
{
  "printers": [
    {
      "id": "printer-1",
      "displayName": "Local Simulated Printer",
      "name": "Local Simulated Printer",
      "portName": "SIM_PORT_1",
      "mode": "simulated",
      "enabled": true,
      "state": "IDLE",
      "hotendTemperature": 21.80,
      "bedTemperature": 21.52
    }
  ]
}
```

---

## Read one printer directly

```bash
curl http://localhost:18080/printers/printer-1
```

Read only the status snapshot:

```bash
curl http://localhost:18080/printers/printer-1/status
```

---

## Disable and enable a printer

Disable:

```bash
curl -X POST http://localhost:18080/printers/printer-1/disable
```

Expected result:

* `enabled` becomes `false`
* state becomes `DISCONNECTED`

Enable again:

```bash
curl -X POST http://localhost:18080/printers/printer-1/enable
```

Expected result:

* `enabled` becomes `true`
* monitoring resumes
* state returns to `IDLE` after a successful poll

---

## Update a printer

```bash
curl -X PUT http://localhost:18080/printers/printer-1 \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Updated Simulated Printer",
    "portName": "SIM_PORT_2",
    "mode": "sim-error",
    "enabled": true
  }'
```

This is useful to test failure behavior.

Expected result:

* printer configuration changes
* monitoring restarts for the updated node
* a failure-mode printer may move to `ERROR`

---

## Delete a printer

```bash
curl -X DELETE http://localhost:18080/printers/printer-1
```

Expected result:

```json
{"deleted":"printer-1"}
```

Check again:

```bash
curl http://localhost:18080/printers
```

---

## Open dashboard

Open in a browser:

```text
http://localhost:18080/dashboard
```

The dashboard reads runtime state through the REST API.

It is useful to verify:

```text
browser -> REST API -> runtime state cache -> monitored printers
```

---

## Current API surface

Main endpoints:

```text
GET  /health
GET  /printers
POST /printers
GET  /printers/{id}
PUT  /printers/{id}
DELETE /printers/{id}
GET  /printers/{id}/status
POST /printers/{id}/enable
POST /printers/{id}/disable
GET  /dashboard
GET  /dashboard/dashboard.css
GET  /dashboard/dashboard.js
```

---

## Real hardware note

Simulation mode is sufficient for local verification.

For real hardware:

* connect the printer by USB
* identify the serial device, preferably a stable `/dev/serial/by-id/...` path
* ensure the user has `dialout` access

Example device check:

```bash
ls /dev/ttyUSB*
ls -l /dev/serial/by-id/
```

Linux names such as `/dev/ttyUSB0` and `/dev/ttyACM0` can change after unplug/replug, reboot, or connecting another USB serial device. When `/dev/serial/by-id/...` exists, use that full path as `portName`.

If permission errors occur:

```bash
sudo usermod -aG dialout $USER
logout
```

After login, verify:

```bash
groups
```

Expected group membership includes:

```text
dialout
```

Real-printer creation uses the same API, but with:

* a real serial device path as `portName`
* `"mode": "real"`

Example:

```bash
curl -X POST http://localhost:18080/printers \
  -H "Content-Type: application/json" \
  -d '{
    "id": "printer-real-1",
    "displayName": "Real Printer",
    "portName": "/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0",
    "mode": "real",
    "enabled": true
  }'
```

On Windows, use the printer COM port, for example `"portName": "COM3"`.

---

## Troubleshooting

### API does not start

Check:

* Java 21 is active
* Maven is installed
* selected API port is free

Health check:

```bash
curl http://localhost:18080/health
```

---

### Printer stays in `ERROR`

Check:

* mode is valid
* simulated mode name is correct
* real serial device path exists
* printer is powered on for real mode
* user has serial-port permission for real mode

---

### Permission denied on real hardware

Check:

```bash
groups
```

Expected:

```text
dialout
```

---

## Next steps

After the quickstart works, continue with:

* `install.md`
* `install-vps.md`
* `test-vps.md`
* `devops.md`
* `developer.md`
* `roadmap.md`
