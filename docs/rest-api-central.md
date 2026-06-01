# SpaghettiChef Central REST API

This document is a practical endpoint reference for the central SpaghettiChef API.

The central API and central dashboard are served from the same host and port.

Example:

```text
http://localhost:19080
```

Most endpoints return JSON. Replay package file endpoints return file bytes.

---

## Conventions

### Base URL

```text
http://localhost:19080
```

### JSON Responses

Most endpoints return:

```http
Content-Type: application/json; charset=utf-8
```

### File Responses

Replay package file endpoints return bytes with a content type inferred from the file extension:

```text
.jpg, .jpeg -> image/jpeg
.png        -> image/png
.json       -> application/json; charset=utf-8
other       -> application/octet-stream
```

### Common Error Response

General API errors use:

```json
{
  "error": "message"
}
```

### CORS

The central API allows dashboard access using:

```text
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, OPTIONS
Access-Control-Allow-Headers: Content-Type
```

### Registration Token

If the central server is configured with a registration token, farm registration requires one of:

```http
X-SpaghettiChef-Registration-Token: secret-token
Authorization: Bearer secret-token
```

The token can be configured with:

```text
CENTRAL_REGISTRATION_TOKEN
spaghettichef.central.registrationToken
```

---

# Core Runtime

## Health and Version

```text
GET /health
GET /version
```

### GET /health

Returns:

```json
{
  "status": "ok",
  "mode": "central"
}
```

### GET /version

Returns the runtime application version:

```json
{
  "version": "0.7.0",
  "mode": "central"
}
```

---

## Central Dashboard Static Resources

```text
GET /central-dashboard
GET /central-dashboard/
GET /central-dashboard/favicon.svg
GET /central-dashboard/central-dashboard.css
GET /central-dashboard/central-dashboard.js
```

---

# Farms

## Farm Endpoints

```text
GET  /api/central/farms
GET  /api/central/farms/
GET  /api/central/farms/overview
POST /api/central/farms/register
GET  /api/central/farms/{farmId}
POST /api/central/farms/{farmId}/heartbeat
GET  /api/central/farms/{farmId}/structure
POST /api/central/farms/{farmId}/structure
```

---

## GET /api/central/farms

Aliases:

```text
GET /api/central/farms/
GET /api/central/farms/overview
```

Returns the central farm overview.

Response shape:

```json
{
  "farms": [
    {
      "status": "ONLINE",
      "farm": {
        "farmId": "farm-1",
        "runtimeInstanceId": "runtime-abc",
        "farmName": "Lab Farm",
        "runtimeVersion": "0.7.0",
        "hostname": "printer-host-1",
        "displayLocation": "Lab A",
        "enabled": true,
        "registeredAt": "2026-05-28T12:00:00Z",
        "lastSeenAt": "2026-05-28T12:01:00Z",
        "printerCount": 4,
        "cameraCount": 4,
        "activePrintCount": 1,
        "warningCount": 0,
        "errorCount": 0,
        "spaghettiAlertCount": 0
      }
    }
  ]
}
```

---

## POST /api/central/farms/register

Registers a local runtime as a central farm.

Request body:

```json
{
  "runtimeInstanceId": "runtime-abc",
  "farmName": "Lab Farm",
  "runtimeVersion": "0.7.0",
  "hostname": "printer-host-1",
  "displayLocation": "Lab A",
  "description": "Development printer farm",
  "metadata": {
    "site": "lab-a"
  }
}
```

Response shape:

```json
{
  "farm": {
    "farmId": "farm-1",
    "runtimeInstanceId": "runtime-abc",
    "farmName": "Lab Farm",
    "runtimeVersion": "0.7.0",
    "hostname": "printer-host-1",
    "displayLocation": "Lab A",
    "enabled": true,
    "registeredAt": "2026-05-28T12:00:00Z",
    "lastSeenAt": "2026-05-28T12:00:00Z",
    "printerCount": 0,
    "cameraCount": 0,
    "activePrintCount": 0,
    "warningCount": 0,
    "errorCount": 0,
    "spaghettiAlertCount": 0,
    "farmSecret": "farm-secret"
  }
}
```

Keep `farmSecret`; heartbeat, structure push, and replay uploads use it to authenticate the farm.

---

## GET /api/central/farms/{farmId}

Returns one farm overview.

Response shape:

```json
{
  "status": "ONLINE",
  "farm": {
    "farmId": "farm-1",
    "runtimeInstanceId": "runtime-abc",
    "farmName": "Lab Farm",
    "runtimeVersion": "0.7.0",
    "hostname": "printer-host-1",
    "displayLocation": "Lab A",
    "enabled": true,
    "registeredAt": "2026-05-28T12:00:00Z",
    "lastSeenAt": "2026-05-28T12:01:00Z",
    "printerCount": 4,
    "cameraCount": 4,
    "activePrintCount": 1,
    "warningCount": 0,
    "errorCount": 0,
    "spaghettiAlertCount": 0
  }
}
```

---

## POST /api/central/farms/{farmId}/heartbeat

Updates farm liveness and summary counters.

Request body:

```json
{
  "runtimeInstanceId": "runtime-abc",
  "farmSecret": "farm-secret",
  "runtimeVersion": "0.7.0",
  "status": "ONLINE",
  "printerCount": 4,
  "cameraCount": 4,
  "activePrintCount": 1,
  "warningCount": 0,
  "errorCount": 0,
  "spaghettiAlertCount": 0,
  "message": "ok"
}
```

Response shape:

```json
{
  "accepted": true,
  "farm": {
    "farmId": "farm-1",
    "runtimeInstanceId": "runtime-abc",
    "farmName": "Lab Farm",
    "runtimeVersion": "0.7.0",
    "hostname": "printer-host-1",
    "displayLocation": "Lab A",
    "enabled": true,
    "registeredAt": "2026-05-28T12:00:00Z",
    "lastSeenAt": "2026-05-28T12:01:00Z",
    "printerCount": 4,
    "cameraCount": 4,
    "activePrintCount": 1,
    "warningCount": 0,
    "errorCount": 0,
    "spaghettiAlertCount": 0
  }
}
```

---

## GET /api/central/farms/{farmId}/structure

Returns the last structure snapshot pushed by a farm.

Response shape:

```json
{
  "farmId": "farm-1",
  "runtimeInstanceId": "runtime-abc",
  "structureUpdatedAt": "2026-05-28T12:01:00Z",
  "structure": {
    "generatedAt": "2026-05-28T12:01:00Z",
    "printers": [],
    "cameras": []
  }
}
```

---

## POST /api/central/farms/{farmId}/structure

Pushes a public structure snapshot for a farm.

Request body:

```json
{
  "runtimeInstanceId": "runtime-abc",
  "farmSecret": "farm-secret",
  "generatedAt": "2026-05-28T12:01:00Z",
  "printers": [
    {
      "printerId": "p1",
      "displayName": "Printer 1",
      "state": "READY"
    }
  ],
  "cameras": [
    {
      "printerId": "p1",
      "enabled": true,
      "sourceType": "ffmpeg"
    }
  ]
}
```

Response shape:

```json
{
  "accepted": true,
  "structureUpdatedAt": "2026-05-28T12:01:00Z"
}
```

Only `generatedAt`, `printers`, and `cameras` are retained in the stored public structure.

---

# Camera Replay Packages

Replay packages are uploaded by a farm as ZIP files. The ZIP must contain `manifest.json`.

## Replay Package Endpoints

```text
GET  /api/central/farms/{farmId}/camera-replay-packages
POST /api/central/farms/{farmId}/camera-replay-packages
GET  /api/central/camera-replay-packages/{packageId}
GET  /api/central/camera-replay-packages/{packageId}/files/{relativePath}
```

---

## GET /api/central/farms/{farmId}/camera-replay-packages

Lists replay packages uploaded by one farm.

Response shape:

```json
{
  "packages": [
    {
      "packageId": "pkg-1",
      "farmId": "farm-1",
      "runtimeInstanceId": "runtime-abc",
      "cameraJobId": "12",
      "printerId": "p1",
      "cameraId": "camera-p1",
      "label": "failed print review",
      "startedAt": "2026-05-28T12:00:00Z",
      "finishedAt": "2026-05-28T12:30:00Z",
      "frameCount": 200,
      "deltaCount": 199,
      "visibility": "private",
      "createdAt": "2026-05-28T12:31:00Z"
    }
  ]
}
```

---

## POST /api/central/farms/{farmId}/camera-replay-packages

Uploads a replay package ZIP for one farm.

Request body:

```text
ZIP bytes
```

The ZIP must contain `manifest.json`. Manifest fields:

```json
{
  "farmId": "farm-1",
  "runtimeInstanceId": "runtime-abc",
  "farmSecret": "farm-secret",
  "cameraJobId": "12",
  "printerId": "p1",
  "cameraId": "camera-p1",
  "label": "failed print review",
  "startedAt": "2026-05-28T12:00:00Z",
  "finishedAt": "2026-05-28T12:30:00Z",
  "frameCount": 200,
  "deltaCount": 199,
  "visibility": "private"
}
```

`farmSecret` may also be supplied with:

```http
X-SpaghettiChef-Farm-Secret: farm-secret
```

Response shape:

```json
{
  "accepted": true,
  "package": {
    "packageId": "pkg-1",
    "farmId": "farm-1",
    "runtimeInstanceId": "runtime-abc",
    "cameraJobId": "12",
    "printerId": "p1",
    "cameraId": "camera-p1",
    "label": "failed print review",
    "startedAt": "2026-05-28T12:00:00Z",
    "finishedAt": "2026-05-28T12:30:00Z",
    "frameCount": 200,
    "deltaCount": 199,
    "visibility": "private",
    "createdAt": "2026-05-28T12:31:00Z"
  }
}
```

The stored package manifest excludes `farmSecret`.

---

## GET /api/central/camera-replay-packages/{packageId}

Returns replay package metadata, stored public manifest, and files.

Response shape:

```json
{
  "package": {
    "packageId": "pkg-1",
    "farmId": "farm-1",
    "runtimeInstanceId": "runtime-abc",
    "cameraJobId": "12",
    "printerId": "p1",
    "cameraId": "camera-p1",
    "label": "failed print review",
    "startedAt": "2026-05-28T12:00:00Z",
    "finishedAt": "2026-05-28T12:30:00Z",
    "frameCount": 200,
    "deltaCount": 199,
    "visibility": "private",
    "createdAt": "2026-05-28T12:31:00Z",
    "manifest": {
      "farmId": "farm-1",
      "runtimeInstanceId": "runtime-abc",
      "cameraJobId": "12"
    }
  },
  "files": [
    {
      "fileType": "snapshot",
      "relativePath": "frames/000001.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 18234,
      "url": "/api/central/camera-replay-packages/pkg-1/files/frames/000001.jpg"
    }
  ]
}
```

---

## GET /api/central/camera-replay-packages/{packageId}/files/{relativePath}

Returns one stored replay package file as bytes.

The `{relativePath}` must stay inside the package directory. Path traversal is rejected.

---

# Condensed Endpoint Table

```text
GET  /health
GET  /version

GET  /central-dashboard
GET  /central-dashboard/
GET  /central-dashboard/favicon.svg
GET  /central-dashboard/central-dashboard.css
GET  /central-dashboard/central-dashboard.js

GET  /api/central/farms
GET  /api/central/farms/
GET  /api/central/farms/overview
POST /api/central/farms/register
GET  /api/central/farms/{farmId}
POST /api/central/farms/{farmId}/heartbeat
GET  /api/central/farms/{farmId}/structure
POST /api/central/farms/{farmId}/structure

GET  /api/central/farms/{farmId}/camera-replay-packages
POST /api/central/farms/{farmId}/camera-replay-packages
GET  /api/central/camera-replay-packages/{packageId}
GET  /api/central/camera-replay-packages/{packageId}/files/{relativePath}
```
