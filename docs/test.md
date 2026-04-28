# Test

This document describes the current verification scope for the `0.1.x` local runtime architecture.

PrinterHub is currently in runtime refactoring. The test scope is intentionally limited to the new `0.1.0` backbone.

---

## Quick verification

```bash
mvn test
mvn clean verify
mvn clean package
````

Current expectation:

```text
The project compiles.
The runtime starts.
The API remains responsive.
The monitoring scheduler updates printer state in the background.
```

---

## Current runtime test scope

`0.1.0` verifies the local runtime backbone:

```text
PrinterHub Java Runtime
├── HTTP server thread pool
├── monitoring scheduler
├── one monitoring task per simulated printer
├── runtime printer registry
├── runtime state cache
├── database initializer placeholder
└── simulated serial communication layer
```

Implemented runtime checks:

* API health endpoint responds
* printer list endpoint responds
* three simulated printers are registered
* background monitoring updates `updatedAt`
* API reads from the runtime state cache
* API remains responsive while monitoring is running

---

## Supported endpoints in 0.1.0

```text
GET /health
GET /printers
```

Not yet part of the 0.1.0 runtime:

```text
/dashboard
/jobs
/printer/status
/printers/{id}/history
/config/printers
/config/monitoring-rules
```

These features belonged to the `0.0.x` prototype and will be reintroduced later on top of the new runtime architecture.

---

## Start local runtime

Default API port:

```text
18080
```

Recommended explicit test port:

```bash
mvn exec:java \
  -Dexec.mainClass="printerhub.Main" \
  -Dprinterhub.api.port=18081
```

---

## Manual API checks

Health check:

```bash
curl http://localhost:18081/health
```

Expected result:

```json
{"status":"ok"}
```

Printer state check:

```bash
curl http://localhost:18081/printers
```

Expected result:

```text
Three simulated printers are returned.
Each printer has an id, display name, port, mode, state, and updatedAt value.
```

---

## API responsiveness check

Run while the runtime is active:

```bash
for i in {1..10}; do
  curl -s http://localhost:18081/health
  echo
  sleep 1
done
```

Expected result:

```text
The API returns {"status":"ok"} every time.
```

This verifies that the HTTP server remains responsive while background monitoring is running.

---

## Background monitoring check

Run while the runtime is active:

```bash
watch -n 2 'curl -s http://localhost:18081/printers | jq'
```

Expected result:

```text
The updatedAt values change regularly.
```

This verifies that the monitoring scheduler updates the runtime state cache independently of API requests.

---

## Multi-printer check

Run:

```bash
curl -s http://localhost:18081/printers | jq
```

Expected printer IDs:

```text
printer-1
printer-2
printer-3
```

Expected state:

```text
IDLE
```

Expected response:

```text
ok T:20.0 /0.0 B:20.0 /0.0
```

This verifies that the runtime has multiple simulated printer nodes and not only a single-printer loop.

---

## Threading check

Find the Java process:

```bash
jps -l
```

Then inspect Java threads:

```bash
jstack <PID> | grep -E "pool|HTTP|Scheduled" -n
```

Alternative:

```bash
ps -T -p <PID>
```

Expected result:

```text
Multiple Java threads are visible.
At least one thread pool belongs to the HTTP server.
At least one scheduled executor thread belongs to monitoring.
```

Note:

```text
<PID> must be replaced by the actual Java process ID.
```

---

## Jenkins verification

The Jenkins pipeline for `0.1.0` validates:

```text
branch checkout
Java and Maven environment
mvn clean verify
runtime startup
GET /health
GET /printers
background updatedAt change
archived smoke-test outputs
```

Archived smoke-test files:

```text
target/runtime-smoke.log
target/health.json
target/printers-before.json
target/printers-after.json
```

---

## Current package test target

Current production structure:

```text
src/main/java/printerhub/
├── Main.java
├── PrinterPort.java
├── PrinterSnapshot.java
├── PrinterState.java
├── api/
├── monitoring/
├── persistence/
├── runtime/
└── serial/
```

Current architectural components:

```text
PrinterHubRuntime
PrinterRuntimeNode
PrinterRegistry
PrinterRuntimeStateCache
PrinterMonitoringScheduler
PrinterMonitoringTask
RemoteApiServer
DatabaseInitializer
SimulatedPrinterPort
```

---
 
