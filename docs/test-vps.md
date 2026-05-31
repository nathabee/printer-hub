# Central VPS Viewer Test

This document describes manual verification for the first central read-only VPS
viewer slice.

It covers only:

- central mode startup
- central farm registration
- heartbeat
- overview
- read-only dashboard
- central database initialization

It does not cover replay upload, central actions, user accounts, or local farm
push automation.

---

## Automated Tests

Run the focused central API tests:

```bash
mvn -Dtest=CentralApiServerTest test
```

Run the central tests plus a local health regression check:

```bash
mvn -Dtest=CentralApiServerTest,RemoteApiServerTest#getHealthReturnsOk test
```

Build both jars:

```bash
mvn -DskipTests package
```

Expected artifacts:

```text
target/spaghetti-chef-<version>-all.jar
target/spaghetti-chef-<version>-central-vps.jar
```

---

## Start Central Mode

From source:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.central.CentralMain" \
  -Dspaghettichef.api.port=18180 \
  -Dspaghettichef.central.databaseFile=spaghettichef-central-test.db \
  exec:java
```

Or through the normal main class with explicit central mode:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.mode=central \
  -Dspaghettichef.api.port=18180 \
  -Dspaghettichef.central.databaseFile=spaghettichef-central-test.db \
  exec:java
```

Keep this terminal open.

---

## Health

```bash
curl -s http://localhost:18180/health
```

Expected:

```json
{"status":"ok","mode":"central"}
```

---

## Register Farm

```bash
curl -s -X POST http://localhost:18180/api/central/farms/register \
  -H "Content-Type: application/json" \
  -d '{
    "runtimeInstanceId": "manual-runtime-001",
    "farmName": "Manual Test Farm",
    "runtimeVersion": "1.0.0",
    "hostname": "private-host"
  }'
```

Expected:

```text
HTTP 201
response contains farmId
response contains farmSecret
```

Register the same `runtimeInstanceId` again. Expected:

```text
same farmId is returned
no duplicate farm row is created
```

---

## Heartbeat

Replace `<farmId>` and `<farmSecret>` with values from registration:

```bash
curl -s -X POST http://localhost:18180/api/central/farms/<farmId>/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "runtimeInstanceId": "manual-runtime-001",
    "farmSecret": "<farmSecret>",
    "runtimeVersion": "1.0.0",
    "status": "ONLINE",
    "printerCount": 2,
    "cameraCount": 1,
    "activePrintCount": 1,
    "warningCount": 0,
    "errorCount": 0,
    "spaghettiAlertCount": 0,
    "message": "ok"
  }'
```

Expected:

```text
HTTP 200
accepted is true
lastSeenAt is set
summary counters are updated
```

---

## Rejection Checks

Unknown farm:

```bash
curl -i -X POST http://localhost:18180/api/central/farms/farm-missing/heartbeat \
  -H "Content-Type: application/json" \
  -d '{"runtimeInstanceId":"manual-runtime-001","farmSecret":"bad"}'
```

Expected:

```text
HTTP 403
unknown_farm
```

Mismatched runtime:

```bash
curl -i -X POST http://localhost:18180/api/central/farms/<farmId>/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "runtimeInstanceId": "other-runtime",
    "farmSecret": "<farmSecret>"
  }'
```

Expected:

```text
HTTP 403
runtime_instance_mismatch
```

---

## Overview

```bash
curl -s http://localhost:18180/api/central/farms/overview
```

Expected:

```text
registered farms are returned
status is ONLINE, STALE, OFFLINE, or DISABLED
summary counters are present
farmSecret is not included
```

---

## Dashboard

Open:

```text
http://localhost:18180/central-dashboard
```

Expected:

```text
registered farms are visible
status and summary counters are visible
no printer start/stop/pause/emergency controls are present
```

---

## Database

```bash
sqlite3 spaghettichef-central-test.db '.tables'
sqlite3 spaghettichef-central-test.db \
  'select farm_id,runtime_instance_id,farm_name,last_seen_at,printer_count,camera_count from central_farm;'
```

Expected:

```text
central_farm table exists
registered farm row exists
last_seen_at changes after heartbeat
```

---

## Stop Central Runtime

Stop with:

```text
Ctrl+C
```

Optional port check:

```bash
ss -ltnp | grep 18180
```
