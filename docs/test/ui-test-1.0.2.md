# Local Test — Central Read-Only VPS Viewer

This document describes how to manually test the central read-only VPS viewer locally before changing the Jenkins pipeline.

This test covers:

* `1.0.0 — Safe Central Read-Only Architecture`
* `1.0.1 — Farm Registration and Heartbeat`
* `1.0.2 — Farm Structure Snapshot`

This is an integration smoke test, not a pure unit test.

## Scope

This test proves that the central runtime can:

* start in central mode
* expose central health
* expose the central dashboard
* register a farm
* return a stable `farmId`
* return a `farmSecret`
* accept a farm heartbeat
* update central farm overview
* accept a sanitized farm structure snapshot
* return the pushed structure snapshot
* hide `farmSecret` from read endpoints
* create the central database
* avoid exposing local printer/job/security endpoints in central mode

## Non-Goals

This test does not cover:

* local printer execution
* local camera capture
* replay package upload
* full replay viewer
* user accounts
* roles
* central actions
* central job dispatch
* real VPS deployment
* reverse proxy configuration
* Docker/container deployment

## Prerequisites

From the repository root:

```bash
pwd
```

Expected example:

```text
/home/nathabee/coding/github/spaghetti-chef/develop
```

Required tools:

```bash
java -version
mvn -version
curl --version
sqlite3 --version
python3 --version
```

## Step 1 — Compile

```bash
mvn -DskipTests compile
```

Or, for a stronger local check:

```bash
mvn clean verify
```

## Step 2 — Start the Central Runtime

Open terminal 1.

```bash
rm -f spaghettichef-central-local-test.db

mvn exec:java \
  -Dexec.mainClass="spaghettichef.central.CentralMain" \
  -Dspaghettichef.central.databaseFile=spaghettichef-central-local-test.db \
  -Dspaghettichef.api.port=18180
```

Expected output should include:

```text
SpaghettiChef central read-only VPS viewer started
Health: http://localhost:18180/health
Central dashboard: http://localhost:18180/central-dashboard
```

Open in browser:

```text
http://localhost:18180/central-dashboard
```

## Step 3 — Create Local Test Output Folder

Open terminal 2.

```bash
mkdir -p target/manual-central-test
```

## Step 4 — Check Central Health

```bash
curl -fsS http://localhost:18180/health \
  > target/manual-central-test/central-health.json

cat target/manual-central-test/central-health.json
```

Expected:

```json
{"status":"ok","mode":"central"}
```

Validate:

```bash
python3 - <<'PY'
import json
from pathlib import Path

data = json.loads(Path("target/manual-central-test/central-health.json").read_text(encoding="utf-8"))

assert data.get("status") == "ok", data
assert data.get("mode") == "central", data

print("OK: central health is valid")
PY
```

## Step 5 — Check Initial Farm Overview

```bash
curl -fsS http://localhost:18180/api/central/farms/overview \
  > target/manual-central-test/central-overview-initial.json

cat target/manual-central-test/central-overview-initial.json
```

Expected:

```json
{"farms":[]}
```

Validate:

```bash
python3 - <<'PY'
import json
from pathlib import Path

data = json.loads(Path("target/manual-central-test/central-overview-initial.json").read_text(encoding="utf-8"))

assert "farms" in data, data
assert isinstance(data["farms"], list), data

print("OK: initial central farm overview is valid")
PY
```

## Step 6 — Register a Farm

```bash
curl -fsS -X POST http://localhost:18180/api/central/farms/register \
  -H "Content-Type: application/json" \
  -d '{
    "runtimeInstanceId": "manual-runtime-001",
    "farmName": "Manual Test Farm",
    "runtimeVersion": "1.0.2",
    "hostname": "manual-localhost",
    "displayLocation": "Local Manual Test"
  }' \
  > target/manual-central-test/central-register.json

cat target/manual-central-test/central-register.json
```

Extract `farmId` and `farmSecret`:

```bash
FARM_ID=$(python3 -c 'import json; print(json.load(open("target/manual-central-test/central-register.json"))["farm"]["farmId"])')
FARM_SECRET=$(python3 -c 'import json; print(json.load(open("target/manual-central-test/central-register.json"))["farm"]["farmSecret"])')

echo "FARM_ID=${FARM_ID}"
echo "FARM_SECRET=${FARM_SECRET}"
```

Validate:

```bash
python3 - <<'PY'
import json
from pathlib import Path

data = json.loads(Path("target/manual-central-test/central-register.json").read_text(encoding="utf-8"))
farm = data.get("farm", {})

assert farm.get("farmId"), data
assert farm.get("farmSecret"), data
assert farm.get("runtimeInstanceId") == "manual-runtime-001", data
assert farm.get("farmName") == "Manual Test Farm", data

print("OK: farm registration is valid")
PY
```

## Step 7 — Send Heartbeat

```bash
curl -fsS -X POST "http://localhost:18180/api/central/farms/${FARM_ID}/heartbeat" \
  -H "Content-Type: application/json" \
  -d "{
    \"runtimeInstanceId\": \"manual-runtime-001\",
    \"farmSecret\": \"${FARM_SECRET}\",
    \"runtimeVersion\": \"1.0.2\",
    \"status\": \"ONLINE\",
    \"printerCount\": 2,
    \"cameraCount\": 1,
    \"activePrintCount\": 1,
    \"warningCount\": 0,
    \"errorCount\": 0,
    \"spaghettiAlertCount\": 0,
    \"message\": \"manual heartbeat ok\"
  }" \
  > target/manual-central-test/central-heartbeat.json

cat target/manual-central-test/central-heartbeat.json
```

Validate:

```bash
python3 - <<'PY'
import json
from pathlib import Path

data = json.loads(Path("target/manual-central-test/central-heartbeat.json").read_text(encoding="utf-8"))

assert data.get("accepted") is True, data

print("OK: heartbeat accepted")
PY
```

## Step 8 — Push Farm Structure Snapshot

This tests `1.0.2`.

The pushed structure contains printer and camera metadata only. It is not a printer-control action.

```bash
curl -fsS -X POST "http://localhost:18180/api/central/farms/${FARM_ID}/structure" \
  -H "Content-Type: application/json" \
  -d "{
    \"runtimeInstanceId\": \"manual-runtime-001\",
    \"farmSecret\": \"${FARM_SECRET}\",
    \"generatedAt\": \"2026-05-31T10:30:00Z\",
    \"printers\": [
      {
        \"printerId\": \"printer-1\",
        \"displayName\": \"Manual Printer 1\",
        \"enabled\": true,
        \"status\": \"PRINTING\"
      },
      {
        \"printerId\": \"printer-2\",
        \"displayName\": \"Manual Printer 2\",
        \"enabled\": true,
        \"status\": \"IDLE\"
      }
    ],
    \"cameras\": [
      {
        \"cameraId\": \"camera-1\",
        \"displayName\": \"Manual Camera 1\",
        \"printerId\": \"printer-1\",
        \"enabled\": true
      }
    ]
  }" \
  > target/manual-central-test/central-structure-push.json

cat target/manual-central-test/central-structure-push.json
```

Validate:

```bash
python3 - <<'PY'
import json
from pathlib import Path

data = json.loads(Path("target/manual-central-test/central-structure-push.json").read_text(encoding="utf-8"))

assert data.get("accepted") is True, data

print("OK: structure push accepted")
PY
```

## Step 9 — Read Farm Overview After Heartbeat

```bash
curl -fsS http://localhost:18180/api/central/farms/overview \
  > target/manual-central-test/central-overview-after-heartbeat.json

cat target/manual-central-test/central-overview-after-heartbeat.json
```

Validate:

```bash
python3 - <<'PY'
import json
from pathlib import Path

data = json.loads(Path("target/manual-central-test/central-overview-after-heartbeat.json").read_text(encoding="utf-8"))

farms = data.get("farms", [])
assert farms, data

farm = farms[0]
assert farm.get("status") == "ONLINE", data
assert farm.get("printerCount") == 2, data
assert farm.get("cameraCount") == 1, data
assert farm.get("activePrintCount") == 1, data

print("OK: overview contains heartbeat summary")
PY
```

## Step 10 — Read Single Farm

```bash
curl -fsS "http://localhost:18180/api/central/farms/${FARM_ID}" \
  > target/manual-central-test/central-farm.json

cat target/manual-central-test/central-farm.json
```

Validate:

```bash
python3 - <<'PY'
import json
from pathlib import Path

text = Path("target/manual-central-test/central-farm.json").read_text(encoding="utf-8")
data = json.loads(text)

assert "farmSecret" not in text, data
assert data.get("farmId") or data.get("farm", {}).get("farmId"), data
assert "ONLINE" in text, data
assert "Manual Test Farm" in text, data

print("OK: single farm endpoint is valid and does not leak farmSecret")
PY
```

## Step 11 — Read Structure Snapshot

```bash
curl -fsS "http://localhost:18180/api/central/farms/${FARM_ID}/structure" \
  > target/manual-central-test/central-structure.json

cat target/manual-central-test/central-structure.json
```

Pretty print:

```bash
python3 -m json.tool target/manual-central-test/central-structure.json
```

Validate without brittle grep:

```bash
python3 - <<'PY'
import json
from pathlib import Path

text = Path("target/manual-central-test/central-structure.json").read_text(encoding="utf-8")
data = json.loads(text)

normalized = json.dumps(data, separators=(",", ":"), sort_keys=True)

assert "farmSecret" not in normalized, normalized
assert '"printerId":"printer-1"' in normalized, normalized
assert '"printerId":"printer-2"' in normalized, normalized
assert '"cameraId":"camera-1"' in normalized, normalized
assert '"displayName":"Manual Printer 1"' in normalized, normalized
assert '"displayName":"Manual Camera 1"' in normalized, normalized

print("OK: structure endpoint contains expected printer/camera metadata and does not leak farmSecret")
PY
```

## Step 12 — Check Central Dashboard HTML

```bash
curl -fsS http://localhost:18180/central-dashboard \
  > target/manual-central-test/central-dashboard.html

grep -q 'SpaghettiChef Central' target/manual-central-test/central-dashboard.html

echo "OK: central dashboard HTML is available"
```

## Step 13 — Check Local Runtime Endpoints Are Not Exposed In Central Mode

These endpoints must return `404` or another non-success status in central mode.

```bash
for path in \
  /printers \
  /jobs \
  /settings/security \
  /operator-audit
do
  status=$(curl -sS -o /tmp/central-check-body.txt -w "%{http_code}" "http://localhost:18180${path}")
  echo "${path} -> ${status}"

  if [ "${status}" = "200" ]; then
    echo "ERROR: central mode exposed local endpoint ${path}"
    cat /tmp/central-check-body.txt
    exit 1
  fi
done

echo "OK: central mode does not expose local runtime endpoints"
```

## Step 14 — Check Central Database Tables

Stop the central runtime in terminal 1 with `Ctrl+C`.

Then run:

```bash
sqlite3 spaghettichef-central-local-test.db '.tables'
```

Expected table should include:

```text
central_farm
```

Validate:

```bash
sqlite3 spaghettichef-central-local-test.db '.tables' \
  > target/manual-central-test/central-db-tables.txt

grep -q 'central_farm' target/manual-central-test/central-db-tables.txt

echo "OK: central database contains central_farm table"
```

Optional inspection:

```bash
sqlite3 spaghettichef-central-local-test.db \
  'select farm_id, runtime_instance_id, farm_name, status, printer_count, camera_count, active_print_count from central_farm;'
```

## Step 15 — Summary

This local test is successful when all of the following are true:

* central `/health` returns `{"status":"ok","mode":"central"}`
* farm registration returns `farmId`
* farm registration returns `farmSecret`
* heartbeat returns `accepted=true`
* overview returns the farm as `ONLINE`
* overview returns expected printer and camera counts
* structure push returns `accepted=true`
* structure read contains expected printer/camera metadata
* structure read does not expose `farmSecret`
* single farm read does not expose `farmSecret`
* central dashboard loads
* central mode does not expose `/printers`
* central mode does not expose `/jobs`
* central mode does not expose `/settings/security`
* central mode does not expose `/operator-audit`
* central database contains `central_farm`

## Interpretation

If this manual test passes but Jenkins fails on a grep such as:

```bash
grep -q '"printerId":"printer-1"' target/central-structure.json
```

then the implementation is probably correct and the Jenkins assertion is too brittle.

In that case, replace brittle JSON greps in Jenkins with Python JSON assertions similar to the ones used in this document.
