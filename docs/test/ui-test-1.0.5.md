# UI Test 1.0.5 — Containerized Central VPS Dashboard

This manual test checks the `1.0.5` central VPS container work and gives the
central dashboard real data to display.

It covers:

* central container starts in central mode
* central health is available
* central dashboard is available
* farm registration
* farm heartbeat
* farm structure snapshot
* replay package upload
* read-only dashboard population
* central database and replay storage use the mounted data directory

It does not test local printer control, local camera capture, local job
execution, central actions, users, roles, or live local-farm connections.

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
python3 --version
sqlite3 --version
```

For the container path:

```bash
docker --version
```

## Option A — Test With Maven

Use this if you want the fastest local UI check without building the Docker
image.

Open terminal 1:

```bash
mvn -DskipTests compile

rm -f spaghettichef-central-1.0.5-ui-test.db
rm -rf target/manual-central-1.0.5-replay

mvn exec:java \
  -Dexec.mainClass="spaghettichef.central.CentralMain" \
  -Dspaghettichef.central.databaseFile=spaghettichef-central-1.0.5-ui-test.db \
  -Dspaghettichef.central.replayStorageDir=target/manual-central-1.0.5-replay \
  -Dspaghettichef.api.port=18180
```

Open:

```text
http://localhost:18180/central-dashboard
```

## Option B — Test With Docker

Use this for the real `1.0.5` container deployment path.

Open terminal 1:

```bash
mvn -DskipTests package

mkdir -p target/manual-central-1.0.5-docker-data

docker build -f Dockerfile.central -t spaghettichef-central-vps:1.0.5 .

docker run --rm \
  --name spaghettichef-central-vps-ui-test \
  -p 18180:8080 \
  -e SPAGHETTICHEF_MODE=central \
  -e CENTRAL_MODE=true \
  -e CENTRAL_REGISTRATION_TOKEN=change-me \
  -e CENTRAL_REPLAY_STORAGE_DIR=/data/replay \
  -v "$PWD/target/manual-central-1.0.5-docker-data:/data" \
  spaghettichef-central-vps:1.0.5
```

Open:

```text
http://localhost:18180/central-dashboard
```

Expected:

```text
The dashboard loads.
No local printer controls are visible.
No local camera capture controls are visible.
No start, stop, pause, emergency stop, shell, upload, edit, or delete actions are visible.
```

## Populate Dashboard Data

Open terminal 2 and paste this whole block.

It creates two farms:

* `Kitchen Farm` with printers, cameras, heartbeat counters, and a replay package
* `Garage Farm` with heartbeat counters and a simple structure snapshot

```bash
set -euo pipefail

BASE_URL="http://localhost:18180"
OUT_DIR="target/manual-central-1.0.5-ui"
REGISTRATION_TOKEN="change-me"

mkdir -p "${OUT_DIR}"

echo "== health =="
curl -fsS "${BASE_URL}/health" \
  > "${OUT_DIR}/health.json"
python3 -m json.tool "${OUT_DIR}/health.json"

python3 - <<'PY'
import json
from pathlib import Path

data = json.loads(Path("target/manual-central-1.0.5-ui/health.json").read_text(encoding="utf-8"))
assert data.get("status") == "ok", data
assert data.get("mode") == "central", data
print("OK: central health")
PY

echo "== register kitchen farm =="
curl -fsS -X POST "${BASE_URL}/api/central/farms/register" \
  -H "Content-Type: application/json" \
  -H "X-SpaghettiChef-Registration-Token: ${REGISTRATION_TOKEN}" \
  -d '{
    "runtimeInstanceId": "ui-test-runtime-kitchen-001",
    "farmName": "Kitchen Farm",
    "runtimeVersion": "1.0.5",
    "hostname": "private-kitchen-runtime",
    "displayLocation": "Home LAN / Kitchen",
    "description": "Manual 1.0.5 UI test farm"
  }' \
  > "${OUT_DIR}/register-kitchen.json"

KITCHEN_FARM_ID="$(python3 -c 'import json; print(json.load(open("target/manual-central-1.0.5-ui/register-kitchen.json"))["farm"]["farmId"])')"
KITCHEN_FARM_SECRET="$(python3 -c 'import json; print(json.load(open("target/manual-central-1.0.5-ui/register-kitchen.json"))["farm"]["farmSecret"])')"

echo "KITCHEN_FARM_ID=${KITCHEN_FARM_ID}"

echo "== register garage farm =="
curl -fsS -X POST "${BASE_URL}/api/central/farms/register" \
  -H "Content-Type: application/json" \
  -H "X-SpaghettiChef-Registration-Token: ${REGISTRATION_TOKEN}" \
  -d '{
    "runtimeInstanceId": "ui-test-runtime-garage-001",
    "farmName": "Garage Farm",
    "runtimeVersion": "1.0.5",
    "hostname": "private-garage-runtime",
    "displayLocation": "Home LAN / Garage",
    "description": "Second manual 1.0.5 UI test farm"
  }' \
  > "${OUT_DIR}/register-garage.json"

GARAGE_FARM_ID="$(python3 -c 'import json; print(json.load(open("target/manual-central-1.0.5-ui/register-garage.json"))["farm"]["farmId"])')"
GARAGE_FARM_SECRET="$(python3 -c 'import json; print(json.load(open("target/manual-central-1.0.5-ui/register-garage.json"))["farm"]["farmSecret"])')"

echo "GARAGE_FARM_ID=${GARAGE_FARM_ID}"

echo "== heartbeat kitchen farm =="
curl -fsS -X POST "${BASE_URL}/api/central/farms/${KITCHEN_FARM_ID}/heartbeat" \
  -H "Content-Type: application/json" \
  -d "{
    \"runtimeInstanceId\": \"ui-test-runtime-kitchen-001\",
    \"farmSecret\": \"${KITCHEN_FARM_SECRET}\",
    \"runtimeVersion\": \"1.0.5\",
    \"status\": \"ONLINE\",
    \"printerCount\": 3,
    \"cameraCount\": 2,
    \"activePrintCount\": 1,
    \"warningCount\": 1,
    \"errorCount\": 0,
    \"spaghettiAlertCount\": 1,
    \"message\": \"Kitchen farm is printing with one warning\"
  }" \
  > "${OUT_DIR}/heartbeat-kitchen.json"

echo "== heartbeat garage farm =="
curl -fsS -X POST "${BASE_URL}/api/central/farms/${GARAGE_FARM_ID}/heartbeat" \
  -H "Content-Type: application/json" \
  -d "{
    \"runtimeInstanceId\": \"ui-test-runtime-garage-001\",
    \"farmSecret\": \"${GARAGE_FARM_SECRET}\",
    \"runtimeVersion\": \"1.0.5\",
    \"status\": \"ONLINE\",
    \"printerCount\": 1,
    \"cameraCount\": 1,
    \"activePrintCount\": 0,
    \"warningCount\": 0,
    \"errorCount\": 0,
    \"spaghettiAlertCount\": 0,
    \"message\": \"Garage farm is idle\"
  }" \
  > "${OUT_DIR}/heartbeat-garage.json"

echo "== structure kitchen farm =="
curl -fsS -X POST "${BASE_URL}/api/central/farms/${KITCHEN_FARM_ID}/structure" \
  -H "Content-Type: application/json" \
  -d "{
    \"runtimeInstanceId\": \"ui-test-runtime-kitchen-001\",
    \"farmSecret\": \"${KITCHEN_FARM_SECRET}\",
    \"generatedAt\": \"2026-05-31T12:00:00Z\",
    \"printers\": [
      {
        \"printerId\": \"kitchen-printer-1\",
        \"displayName\": \"Kitchen Ender 3\",
        \"enabled\": true,
        \"status\": \"PRINTING\"
      },
      {
        \"printerId\": \"kitchen-printer-2\",
        \"displayName\": \"Kitchen Sidewinder\",
        \"enabled\": true,
        \"status\": \"IDLE\"
      },
      {
        \"printerId\": \"kitchen-printer-3\",
        \"displayName\": \"Kitchen Resin Spare\",
        \"enabled\": false,
        \"status\": \"DISABLED\"
      }
    ],
    \"cameras\": [
      {
        \"cameraId\": \"kitchen-camera-front\",
        \"displayName\": \"Kitchen Front Camera\",
        \"printerId\": \"kitchen-printer-1\",
        \"enabled\": true
      },
      {
        \"cameraId\": \"kitchen-camera-top\",
        \"displayName\": \"Kitchen Top Camera\",
        \"printerId\": \"kitchen-printer-1\",
        \"enabled\": true
      }
    ]
  }" \
  > "${OUT_DIR}/structure-kitchen-push.json"

echo "== structure garage farm =="
curl -fsS -X POST "${BASE_URL}/api/central/farms/${GARAGE_FARM_ID}/structure" \
  -H "Content-Type: application/json" \
  -d "{
    \"runtimeInstanceId\": \"ui-test-runtime-garage-001\",
    \"farmSecret\": \"${GARAGE_FARM_SECRET}\",
    \"generatedAt\": \"2026-05-31T12:01:00Z\",
    \"printers\": [
      {
        \"printerId\": \"garage-printer-1\",
        \"displayName\": \"Garage CoreXY\",
        \"enabled\": true,
        \"status\": \"IDLE\"
      }
    ],
    \"cameras\": [
      {
        \"cameraId\": \"garage-camera-1\",
        \"displayName\": \"Garage Watch Camera\",
        \"printerId\": \"garage-printer-1\",
        \"enabled\": true
      }
    ]
  }" \
  > "${OUT_DIR}/structure-garage-push.json"

echo "== create replay package zip =="
python3 - <<PY
import json
import zipfile
from pathlib import Path

out_dir = Path("${OUT_DIR}")
farm_id = "${KITCHEN_FARM_ID}"

manifest = {
    "farmId": farm_id,
    "runtimeInstanceId": "ui-test-runtime-kitchen-001",
    "cameraJobId": "ui-test-camera-job-1",
    "printerId": "kitchen-printer-1",
    "cameraId": "kitchen-camera-front",
    "startedAt": "2026-05-31T12:02:00Z",
    "finishedAt": "2026-05-31T12:04:00Z",
    "frameCount": 2,
    "deltaCount": 1,
    "label": "Manual 1.0.5 replay",
    "source": "local-upload"
}

zip_path = out_dir / "manual-central-replay.zip"
with zipfile.ZipFile(zip_path, "w") as archive:
    archive.writestr("manifest.json", json.dumps(manifest))
    archive.writestr("snapshots/000001.jpg", "manual-ui-test-frame-1")
    archive.writestr("snapshots/000002.jpg", "manual-ui-test-frame-2")
    archive.writestr("deltas/000001_000002_delta.jpg", "manual-ui-test-delta")

print(zip_path)
PY

echo "== upload replay package =="
curl -fsS -X POST "${BASE_URL}/api/central/farms/${KITCHEN_FARM_ID}/camera-replay-packages" \
  -H "Content-Type: application/zip" \
  -H "X-SpaghettiChef-Farm-Secret: ${KITCHEN_FARM_SECRET}" \
  --data-binary @"${OUT_DIR}/manual-central-replay.zip" \
  > "${OUT_DIR}/replay-upload.json"

PACKAGE_ID="$(python3 -c 'import json; print(json.load(open("target/manual-central-1.0.5-ui/replay-upload.json"))["package"]["packageId"])')"
echo "PACKAGE_ID=${PACKAGE_ID}"

echo "== read overview =="
curl -fsS "${BASE_URL}/api/central/farms/overview" \
  > "${OUT_DIR}/overview.json"
python3 -m json.tool "${OUT_DIR}/overview.json"

echo "== read kitchen structure =="
curl -fsS "${BASE_URL}/api/central/farms/${KITCHEN_FARM_ID}/structure" \
  > "${OUT_DIR}/structure-kitchen.json"
python3 -m json.tool "${OUT_DIR}/structure-kitchen.json"

echo "== read replay list and detail =="
curl -fsS "${BASE_URL}/api/central/farms/${KITCHEN_FARM_ID}/camera-replay-packages" \
  > "${OUT_DIR}/replay-list.json"
python3 -m json.tool "${OUT_DIR}/replay-list.json"

curl -fsS "${BASE_URL}/api/central/camera-replay-packages/${PACKAGE_ID}" \
  > "${OUT_DIR}/replay-detail.json"
python3 -m json.tool "${OUT_DIR}/replay-detail.json"

echo "== validate populated data =="
python3 - <<'PY'
import json
from pathlib import Path

out = Path("target/manual-central-1.0.5-ui")

overview_text = (out / "overview.json").read_text(encoding="utf-8")
overview = json.loads(overview_text)
farms = overview.get("farms", [])
assert len(farms) >= 2, overview
assert "farmSecret" not in overview_text, overview_text

names = {farm.get("farmName") for farm in farms}
assert "Kitchen Farm" in names, overview
assert "Garage Farm" in names, overview

kitchen = next(farm for farm in farms if farm.get("farmName") == "Kitchen Farm")
assert kitchen.get("status") == "ONLINE", kitchen
assert kitchen.get("printerCount") == 3, kitchen
assert kitchen.get("cameraCount") == 2, kitchen
assert kitchen.get("activePrintCount") == 1, kitchen
assert kitchen.get("warningCount") == 1, kitchen
assert kitchen.get("spaghettiAlertCount") == 1, kitchen

structure_text = (out / "structure-kitchen.json").read_text(encoding="utf-8")
structure_response = json.loads(structure_text)
assert "farmSecret" not in structure_text, structure_text
structure = structure_response.get("structure", {})
printer_ids = {printer.get("printerId") for printer in structure.get("printers", [])}
camera_ids = {camera.get("cameraId") for camera in structure.get("cameras", [])}
assert "kitchen-printer-1" in printer_ids, structure_response
assert "kitchen-camera-front" in camera_ids, structure_response

replay_text = (out / "replay-detail.json").read_text(encoding="utf-8")
replay = json.loads(replay_text)
assert "farmSecret" not in replay_text, replay_text
assert replay.get("packageId"), replay
assert "manifest" in replay, replay
assert replay["manifest"].get("cameraJobId") == "ui-test-camera-job-1", replay
assert any(file.get("relativePath") == "snapshots/000001.jpg" for file in replay.get("files", [])), replay

print("OK: central 1.0.5 UI data is populated and read responses hide farmSecret")
PY

echo
echo "Open the dashboard and refresh:"
echo "${BASE_URL}/central-dashboard"
echo
echo "Expected dashboard data:"
echo "- Fleet cards show at least 2 farms"
echo "- Kitchen Farm is ONLINE with 3 printers, 2 cameras, 1 active print, 1 warning, 1 spaghetti alert"
echo "- Garage Farm is ONLINE with 1 printer and 1 camera"
echo "- Structure view shows Kitchen Ender 3, Kitchen Sidewinder, Kitchen Front Camera, Kitchen Top Camera"
echo "- Replay list shows Manual 1.0.5 replay"
echo "- Replay player can show snapshots/000001.jpg and snapshots/000002.jpg"
```

## Browser Checklist

Refresh:

```text
http://localhost:18180/central-dashboard
```

Confirm:

* farm status cards show populated totals
* farm overview table lists `Kitchen Farm` and `Garage Farm`
* `Kitchen Farm` is shown as `ONLINE`
* `Kitchen Farm` shows `3` printers and `2` cameras
* `Kitchen Farm` shows `1` active print, `1` warning, and `1` spaghetti alert
* structure view shows the kitchen printers and cameras
* replay package list includes `Manual 1.0.5 replay`
* replay player can display the uploaded replay package frames
* there are no start, stop, pause, emergency stop, upload, edit, delete, shell, or local runtime action buttons

## Check Container Persistence

For Docker mode, stop the container with `Ctrl+C` in terminal 1, then inspect:

```bash
sqlite3 target/manual-central-1.0.5-docker-data/spaghettichef-central.db '.tables'

sqlite3 target/manual-central-1.0.5-docker-data/spaghettichef-central.db \
  'select farm_id,runtime_instance_id,farm_name,status,printer_count,camera_count,active_print_count,warning_count,spaghetti_alert_count from central_farm;'

find target/manual-central-1.0.5-docker-data/replay -maxdepth 4 -type f | sort
```

Expected:

```text
central_farm
central_replay_package
central_replay_file
Kitchen Farm and Garage Farm rows exist
uploaded replay files exist under target/manual-central-1.0.5-docker-data/replay
```

For Maven mode, stop the runtime with `Ctrl+C`, then inspect:

```bash
sqlite3 spaghettichef-central-1.0.5-ui-test.db '.tables'

sqlite3 spaghettichef-central-1.0.5-ui-test.db \
  'select farm_id,runtime_instance_id,farm_name,status,printer_count,camera_count,active_print_count,warning_count,spaghetti_alert_count from central_farm;'

find target/manual-central-1.0.5-replay -maxdepth 4 -type f | sort
```

## Local Endpoint Safety Check

With central runtime still running, local-only endpoints must not be exposed.

```bash
for path in \
  /printers \
  /jobs \
  /settings/security \
  /operator-audit
do
  status="$(curl -sS -o /tmp/central-1.0.5-local-endpoint-body.txt -w "%{http_code}" "http://localhost:18180${path}")"
  echo "${path} -> ${status}"

  if [ "${status}" = "200" ]; then
    echo "ERROR: central mode exposed local endpoint ${path}"
    cat /tmp/central-1.0.5-local-endpoint-body.txt
    exit 1
  fi
done

echo "OK: central mode does not expose local runtime endpoints"
```

## Success Criteria

This manual UI test passes when:

* central `/health` returns central mode
* dashboard loads at `/central-dashboard`
* curls create visible farm data
* dashboard shows farm summary, structure, and replay package data
* read APIs do not expose `farmSecret`
* Docker mode stores DB and replay files under the mounted data directory
* central mode does not expose local runtime endpoints
