# Install Central VPS Viewer

This document describes how to run the first central read-only SpaghettiChef VPS
viewer.

The central VPS viewer is not a local farm runtime. It does not initialize
printer communication, camera capture, local jobs, or printer monitoring. It
stores only central read-only state such as farm registration and latest
heartbeat summaries.

---

## Requirements

Runtime machine:

```text
Java 21
curl, optional for smoke tests
sqlite3, optional for database inspection
Docker, optional for container deployment
Nginx or Apache, optional as HTTPS reverse proxy
```

The first central slice uses SQLite by default. Put the central database on a
persistent disk or mounted volume.

---

## Build From Source

```bash
mvn clean package
```

Expected artifacts:

```text
target/spaghetti-chef-<version>-all.jar
target/spaghetti-chef-<version>-central-vps.jar
```

The `all.jar` artifact remains the local farm runtime. The `central-vps.jar`
artifact is the central read-only VPS runtime.

---

## Run Central Locally

```bash
java \
  -Dspaghettichef.api.port=18180 \
  -Dspaghettichef.central.databaseFile=spaghettichef-central.db \
  -jar target/spaghetti-chef-<version>-central-vps.jar
```

Open:

```text
http://localhost:18180/central-dashboard
```

Health check:

```bash
curl -s http://localhost:18180/health
```

Expected:

```json
{"status":"ok","mode":"central"}
```

---

## Run Local Farm And Central On One Machine

Terminal 1, local farm:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.local.LocalMain" \
  -Dspaghettichef.api.port=18080 \
  -Dspaghettichef.monitoring.intervalSeconds=1 \
  -Dspaghettichef.databaseFile=spaghettichef-local.db \
  exec:java
```

Open:

```text
http://localhost:18080/dashboard
```

Terminal 2, central VPS viewer:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.central.CentralMain" \
  -Dspaghettichef.api.port=18180 \
  -Dspaghettichef.central.databaseFile=spaghettichef-central.db \
  exec:java
```

Open:

```text
http://localhost:18180/central-dashboard
```

The two runtimes must use different ports and different database files.

---

## Manual Registration Smoke Test

The 1.0.1 slice does not include the local farm push client yet. Use
`curl` to simulate the local farm pushing selected data to central.

If `CENTRAL_REGISTRATION_TOKEN` or
`-Dspaghettichef.central.registrationToken=...` is configured, registration
requests must include one of:

```text
X-SpaghettiChef-Registration-Token: <token>
Authorization: Bearer <token>
```

Register:

```bash
curl -s -X POST http://localhost:18180/api/central/farms/register \
  -H "Content-Type: application/json" \
  -H "X-SpaghettiChef-Registration-Token: <token-if-configured>" \
  -d '{
    "runtimeInstanceId": "vps-test-runtime-001",
    "farmName": "VPS Test Farm",
    "runtimeVersion": "1.0.0",
    "hostname": "private-local-farm"
  }'
```

The response includes:

```text
farmId
farmSecret
```

Heartbeat:

```bash
curl -s -X POST http://localhost:18180/api/central/farms/<farmId>/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "runtimeInstanceId": "vps-test-runtime-001",
    "farmSecret": "<farmSecret>",
    "runtimeVersion": "1.0.0",
    "status": "ONLINE",
    "printerCount": 1,
    "cameraCount": 0,
    "activePrintCount": 0,
    "warningCount": 0,
    "errorCount": 0,
    "spaghettiAlertCount": 0,
    "message": "ok"
  }'
```

Overview:

```bash
curl -s http://localhost:18180/api/central/farms/overview
```

Single farm:

```bash
curl -s http://localhost:18180/api/central/farms/<farmId>
```

---

## Container Package

Jenkins release builds produce a central VPS container bundle:

```text
spaghetti-chef-<version>-central-vps-container.tar.gz
```

Extract it:

```bash
tar -xzf spaghetti-chef-<version>-central-vps-container.tar.gz
cd central
```

Build the container:

```bash
docker build -f Dockerfile -t spaghettichef-central-vps .
```

Run with a persistent data directory:

```bash
mkdir -p data

docker run --rm \
  -p 8080:8080 \
  -e SPAGHETTICHEF_MODE=central \
  -e CENTRAL_MODE=true \
  -e CENTRAL_REGISTRATION_TOKEN=change-me \
  -e JAVA_OPTS="-Dspaghettichef.central.databaseFile=/data/spaghettichef-central.db" \
  -v "$PWD/data:/data" \
  spaghettichef-central-vps
```

Open:

```text
http://<vps-host>:8080/central-dashboard
```

---

## Reverse Proxy Direction

For a real VPS, put HTTPS in front of the Java container with Nginx or Apache.

Example Nginx shape:

```nginx
server {
    listen 443 ssl;
    server_name central.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

Do not expose a local farm port to the public internet. The intended direction
is local farm to central VPS only.

---

## Central Database

Inspect:

```bash
sqlite3 spaghettichef-central.db '.tables'
sqlite3 spaghettichef-central.db \
  'select farm_id,runtime_instance_id,farm_name,last_seen_at from central_farm;'
```

Expected table:

```text
central_farm
```

The central database is not the local farm database.
