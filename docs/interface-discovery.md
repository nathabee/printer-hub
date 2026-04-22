# Interface Discovery Log

This document records all steps taken to discover and validate communication with the printer hardware.

It functions as a technical lab notebook.

---

# Prerequise


* Ubuntu Linux

Initial Tools:

* `minicom` (serial communication testing)
* Java (planned)
* Spring Boot (planned)
* Arduino (future simulation work)


* installation : 
sudo apt install minicom


* info about screen (universal tool but we do not use it , we use minicom)

sudo  apt install screen

sudo screen -L -Logfile printer.log /dev/ttyUSB0 115200
cat printer.log

## info 

### minicom

minimal navigation :
```
Ctrl + A  then  B   → scroll back
Esc                    → leave scroll mode

Ctrl + A  then  X   → exit minicom
Ctrl + A  then  Z   → show help
 
```

Description :
```
Ctrl + A
then
O

Configuration menu
|_ Screen

change Add linefeed → Yes

esc
Save setup as dfl
Exit

Ctrl + A
then
U

That toggles:

Add Carriage Return

```


make the command for example :
```
M105
```

ok T:22.27 /0.00 B:22.11 /0.00 @:0 B@:0




### screen
sudo  apt install screen
To exit screen, do exactly:

Ctrl + A
then
K
then
Y
 





---

# Hardware Identification

Printer Model:

Creality Ender-3 V2 Neo

Connection Type:

USB-C → USB-A

Host System:

Ubuntu Linux

---

# Step 1 — Detect USB Device

Command:

```bash
sudo dmesg | tail
```

Observed Output:
```
usb 1-1: New USB device found
idVendor=1a86
idProduct=7523
Product: USB Serial

ch341-uart converter detected
attached to ttyUSB0
```

<details>
<summary>Result</summary>
```
[81347.360259] usb 1-1: new full-speed USB device number 15 using xhci_hcd
[81347.484227] usb 1-1: New USB device found, idVendor=1a86, idProduct=7523, bcdDevice= 2.64
[81347.484245] usb 1-1: New USB device strings: Mfr=0, Product=2, SerialNumber=0
[81347.484251] usb 1-1: Product: USB Serial
[81347.592129] usbcore: registered new interface driver usbserial_generic
[81347.592142] usbserial: USB Serial support registered for generic
[81347.593547] usbcore: registered new interface driver ch341
[81347.593563] usbserial: USB Serial support registered for ch341-uart
[81347.593576] ch341 1-1:1.0: ch341-uart converter detected
[81347.594869] usb 1-1: ch341-uart converter now attached to ttyUSB0
``` 
</details>


Conclusion:

The printer exposes a **USB serial interface** using:

CH341 USB-to-UART converter

Device path:

```
/dev/ttyUSB0
```

---

# Step 2 — Identify Serial Port

Command:

```bash
ls /dev/ttyUSB*
```

Expected:

```
/dev/ttyUSB0
```

Purpose:

Confirm communication channel availability.

---

# Step 3 — Open Serial Communication

Command:

```bash 

sudo minicom -D /dev/ttyUSB0 -b 115200
```

Baud Rate:

```
115200
```

Reason:

Standard serial speed used by Marlin firmware.

---

# Step 4 — Send Test Command

Command:

```
M115
```

Purpose:

Retrieve firmware information.

Expected Response:

``` 

FIRMWARE_NAME:Marlin...
       
```
<details>
<summary>Result</summary>

``` 

FIRMWARE_NAME:Marlin V1.1.8C (Mar 24 2023 09:30:12) SOURCE_CODE_URL:github.com/MarlinFirmware/Marlin PROTOCOL_VERSION:1.0 MACHIf
       
```

</details>

---

# Step 5 — Temperature Query

Command:

```
M105
```

Purpose:

Read temperature values.

Expected Response:

```
ok T:xx.x /0.0 B:xx.x /0.0
```

<details>
<summary>Result</summary>

``` 

ok T:22.27 /0.00 B:22.11 /0.00 @:0 B@:0


```

</details>

---

# Communication Model

Current system:

```text
Ubuntu PC
    |
USB Serial (/dev/ttyUSB0)
    |
CH341 Converter
    |
STM32 MCU
    |
Marlin Firmware
```

---

# Known Protocol

Protocol:

G-code over serial.

Example commands:

### safe information commands
```
M105   Read temperature (hotend and bed)
M114   Read current position (X Y Z E)
M115   Get firmware information
M503   Show current configuration (EEPROM settings)
M119   Show endstop status
M27    Get SD print status
M20    List files on SD card
M31    Show print time
M211   Show software endstop limits
M155 S2   Enable automatic temperature reporting every 2 seconds
M155 S0   Disable automatic temperature reporting
M17    Enable motors (no movement)
M18    Disable motors (release steppers)
M400   Wait until moves are finished (sync command)
```
### control action commands

```
M104 S200   Set hotend temperature (no waiting)
M140 S60    Set bed temperature (no waiting)
M106 S255   Turn fan on full speed
M107        Turn fan off
M300 S1000 P500   Beep sound (if buzzer exists)
```

### Movement commands (only later)

Don't use these yet unless printer is clear and safe.
 

```
G28         Home all axes
G1 X50 Y50 F3000   Move head to position
```


---

# Observations
 

Examples:

* Response latency
* Connection stability
* Timeout behavior
* Unexpected output patterns

---
