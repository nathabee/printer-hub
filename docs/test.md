# Test

This document describes the verification scope for the `0.1.x` local runtime architecture.

> PrinterHub is currently in runtime migration.
> The tests focus on proving that the new runtime structure starts correctly, keeps the API responsive, and updates printer state through background monitoring.
> Feature-specific details belong in the roadmap and version notes, not in this file.

---

## Quick verification

```bash
mvn test
mvn clean verify
mvn clean package
````

Expected result:

```text
BUILD SUCCESS
```

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

Keep this terminal open while running the manual checks below.

---

## Health check

```bash
curl -s http://localhost:18081/health
```

Expected result:

```json
{"status":"ok"}
```

---

## Printer list check

```bash
curl -s http://localhost:18081/printers | jq
```

Expected result:

```text
Configured printers are returned.
Each printer has an id, display name, port, mode, state, and updatedAt value.
```

---

## Background monitoring check

```bash
watch -n 2 'curl -s http://localhost:18081/printers | jq'
```

Expected result:

```text
updatedAt changes regularly.
Printer state is updated without calling hardware directly from the API.
```

---

## Temperature parsing check

```bash
curl -s http://localhost:18081/printers | jq
```

Expected result:

```text
Normal simulated printers show parsed hotend and bed temperature values.
The latest response contains a printer status response.
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

## Multi-printer monitoring check

```bash
curl -s http://localhost:18081/printers | jq
```

Expected result:

```text
Multiple printer nodes are returned.
Each printer has its own cached state.
A failed printer must not prevent other printers from updating.
```

---

## State refresh comparison

```bash
curl -s http://localhost:18081/printers | jq > /tmp/printers-before.json
sleep 5
curl -s http://localhost:18081/printers | jq > /tmp/printers-after.json
diff /tmp/printers-before.json /tmp/printers-after.json
```

Expected result:

```text
updatedAt values changed.
The API remained responsive.
```

Note:

```text
A visible diff is expected because monitoring updates timestamps.
```

---

## Threading check

Find the Java process:

```bash
jps -l
```

Inspect Java threads:

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

## Stop local runtime

Stop the running process with:

```text
Ctrl+C
```

Expected result:

```text
The runtime shuts down without leaving the test port occupied.
```

Optional port check:

```bash
ss -ltnp | grep 18081
```

Expected result:

```text
No process is listening on port 18081.
```

---

## Jenkins verification

The Jenkins pipeline should validate:

```text
branch checkout
Java and Maven environment
mvn clean verify
runtime startup
GET /health
GET /printers
background state refresh
archived smoke-test outputs
```

Expected archived smoke-test files:

```text
target/runtime-smoke.log
target/health.json
target/printers-before.json
target/printers-after.json
```

--- 
##################################################

MERGE WITH  test extract from 0.1.2 step A

---

## Step A test commands

Start:

```bash
mvn exec:java \
  -Dexec.mainClass="printerhub.Main" \
  -Dprinterhub.api.port=18081
```

List:

```bash
curl -s http://localhost:18081/printers | jq
```

Add sim printer:

```bash
curl -s -X POST http://localhost:18081/printers \
  -H "Content-Type: application/json" \
  -d '{
    "id": "printer-4",
    "displayName": "Simulated Printer 4",
    "portName": "SIM_PORT_4",
    "mode": "simulated",
    "enabled": true
  }' | jq
```

Check it appears and gets monitored:

```bash
watch -n 2 'curl -s http://localhost:18081/printers | jq'
```

Disable:

```bash
curl -s -X POST http://localhost:18081/printers/printer-4/disable | jq
```

Enable:

```bash
curl -s -X POST http://localhost:18081/printers/printer-4/enable | jq
```

Update:

```bash
curl -s -X PUT http://localhost:18081/printers/printer-4 \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Updated Simulated Printer 4",
    "portName": "SIM_PORT_4",
    "mode": "sim-error",
    "enabled": true
  }' | jq
```

Status:

```bash
curl -s http://localhost:18081/printers/printer-4/status | jq
```

Delete:

```bash
curl -s -X DELETE http://localhost:18081/printers/printer-4 | jq
```

Then:

```bash
curl -s http://localhost:18081/printers | jq
```

Expected:

```text
printer-4 is gone
other printers still update
API stays responsive
```
 
---
#####################################
test for persistence
##############################


```bash
sqlite3 printerhub.db '.tables'
sqlite3 printerhub.db 'select printer_id,state,created_at from printer_snapshots order by id desc limit 10;'
sqlite3 printerhub.db 'select printer_id,event_type,message,created_at from printer_events order by id desc limit 10;'



``` 