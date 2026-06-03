
# SpaghettiChef Linux Tools

This directory contains Linux helper scripts for SpaghettiChef.

## Camera Helpers

SpaghettiChef now supports direct ffmpeg webcam capture from the dashboard. The
camera helper scripts are mainly diagnostic tools and fallback utilities.

```text
tools/linux/camera/
├── camera-capture-loop.sh
└── camera-capture-once.sh
````

## Requirements

```bash
sudo apt install v4l-utils ffmpeg
```

## Find the Camera

```bash
lsusb
v4l2-ctl --list-devices
```

Example device:

```text
/dev/video0
```

## Dashboard ffmpeg Backend

In the selected printer Camera view, configure:

```text
Source type: ffmpeg webcam
Source value: /dev/video0
ffmpeg command: ffmpeg
ffmpeg input format: v4l2
ffmpeg video size: 640x480
```

## Capture One Image

```bash
CAMERA_DEVICE=/dev/video0 \
tools/linux/camera/camera-capture-once.sh ./data/printers/p1/camera/latest.jpg
```

## Run Capture Loop

```bash
CAMERA_DEVICE=/dev/video0 \
CAMERA_BASE_DIR=./data/printers/p1/camera \
CAMERA_INTERVAL_SECONDS=2 \
CAMERA_ARCHIVE_INTERVAL_SECONDS=300 \
CAMERA_RETENTION_HOURS=24 \
tools/linux/camera/camera-capture-loop.sh
```

## Quick Archive Test

```bash
CAMERA_DEVICE=/dev/video0 \
CAMERA_ARCHIVE_INTERVAL_SECONDS=10 \
tools/linux/camera/camera-capture-loop.sh
```

## Expected Result

After the loop has run for 15 to 20 seconds:

```bash
ls -lh ./data/printers/p1/camera
ls -lh ./data/printers/p1/camera/snapshots
```

Expected files:

```text
latest.jpg
previous.jpg
snapshots/*.jpg
```

## Storage Layout

When the database is `./data/spaghettichef.db`, the default camera storage is:

```text
./data/printers/<printerId>/camera/
├── latest.jpg
├── previous.jpg
└── snapshots/
```

Use the same `printerId` as the printer created in the SpaghettiChef dashboard.
