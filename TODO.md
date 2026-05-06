# TODO ab 0.2.3 step C onwards


here are the step to be done :
## 0.2.3 step C — Correct execution diagnostics and classified outcomes
## 0.2.3 step D — File-backed print jobs and print-asset validation
## 0.2.3 step E — Controlled real-printer print-start workflow
## 0.2.3 step F — Running print supervision, pause/cancel, and terminal reviewability
## 0.2.4 — Packaging Local Runtime
## 0.2.5 — Runtime Recovery and Serial Device Robustness



 

 
## 2 Pipelines


On a stock Ender-style workflow, the print path is roughly:

1. prepare and validate the printer,
2. prepare or obtain a valid `.gcode` file,
3. make that file available to the printer,
4. select and start the print,
5. monitor progress and detect failure states,
6. pause/cancel/finish with reviewable history. ([Manuals+][1])


There are really **two different pipelines**:

### 1. Host-side preparation pipeline

This is outside the printer itself:

* import/open model or project
* choose printer profile
* choose filament/process preset
* slice to `.gcode`
* preview/check the result
* export the file for the printer or send it over a supported transport. ([Creality Wiki][2])

### 2. Printer-side execution pipeline

This is the Ender-like machine workflow:

* printer ready check
* leveling / Z-offset / homing / heating as needed
* file selection
* print start
* progress/status tracking
* pause / cancel / completion / failure handling. ([Manuals+][1])

That distinction matters because **`0.2.3` can realistically finish the printer-side runtime orchestration**, but **full slicing/model preparation belongs either to a later step or to an explicitly external workflow** unless you want PrinterHub to become a slicer host too. ([Creality Wiki][2])

## What the real Ender-style subprocesses are

If your target is “at the end of 0.2.3 I can launch a real print,” the subprocesses are these:

## A. Print asset intake

This is the first missing block.

Subprocess:

* accept a print asset
* distinguish model source vs already-sliced G-code
* validate filename / extension / basic format
* store metadata
* bind the asset to a job

What matters architecturally:

* If the input is already `.gcode`, PrinterHub can stay out of slicing.
* If the input is STL/OBJ/3MF, then PrinterHub must either reject it for now or introduce a slicing integration layer.

This is the first place where you need a hard product decision. Creality’s own flow explicitly includes model import, preset selection, slicing, preview, then export/send. ([Creality Wiki][2])

## B. Printer readiness and preparation

This is the part that already fits your Ender-like UI best.

Subprocess:

* printer reachable
* printer enabled
* no active conflicting job
* port/session ownership locked
* state known and fresh
* optional auto home
* optional leveling/Z-offset prerequisites
* optional bed/nozzle preheat
* optional operator confirmation if preconditions are not ideal

The Ender V2 Neo manual explicitly ties `Auto home`, `Move Z`, `Z-offset`, `Disable stepper`, and reset/config-style operations to the menu paths you already mapped. ([Manuals+][1])

## C. File availability on the printer side

This is the biggest missing concept in your current roadmap.

Subprocess:

* make the selected G-code file available to the printer
* choose transport mode

For a stock Ender-like workflow, the natural path is **SD-card based printing**: export the G-code to card, then the printer selects and prints it. Creality’s software guide explicitly describes exporting to SD card, inserting it into the printer, then selecting the file from the printer UI. Marlin also documents the SD-print command family: `M20/M21/M23/M24/M25/M27`. ([Creality Wiki][2])

This is where you need another hard decision:

* **Option 1: SD-managed workflow**
  PrinterHub manages job metadata and operator guidance, but the operator still physically inserts/selects the file on the printer.
* **Option 2: Host-managed remote start**
  PrinterHub sends/selects/starts SD print via serial-supported commands where firmware behavior allows it.
* **Option 3: Host streaming workflow**
  PrinterHub streams G-code line by line like OctoPrint. That is a much bigger architecture jump.

For `0.2.3`, only **Option 1 or a limited Option 2** is realistic.

## D. Controlled start of print execution

Subprocess:

* select file
* start print
* transition job into running
* correlate runtime job with printer execution state

On Marlin-like firmware, SD printing is represented by selecting the file and starting it, for example `M23` then `M24`; `M25` pauses and `M27` reports SD print status. ([Marlin Firmware][3])

This is where your current “one job = one semantic action” model starts to break down. A real print is no longer one semantic command. It is a **compound workflow job**.

## E. Progress monitoring and execution diagnostics

Subprocess:

* polling while printing
* distinguish expected heating/waiting from fault states
* track percentage / elapsed / active file if available
* detect stop conditions
* store raw response evidence
* classify outcome

This is exactly where your planned `0.2.3 step C` belongs.

## F. Pause / cancel / failure / completion handling

Subprocess:

* pause safely
* cancel safely
* detect abnormal stop
* mark completed only on real terminal evidence
* persist final outcome and operator-visible explanation

This is not just audit polish. This is part of a usable print workflow.

---

# What to add to 0.2.3 from step C onward

 

## 0.2.3 — Local Audit, History Views, and Controlled Job Actions

Status stays `in progress`.

### step C — Correct execution diagnostics and classified outcomes

Keep this, but sharpen it.

Goals:

* persist the actual printer response that led to success or failure
* classify outcomes into:

  * success
  * printer-reported error
  * timeout / no response
  * communication failure
  * validation failure
  * orchestration failure
* persist workflow step diagnostics, not only final job diagnostics
* show command, response, step name, classified outcome, and failure detail in dashboard/API history

This is necessary before real print orchestration, otherwise later failures will be unreadable.

### step D — Print asset intake and file-backed jobs

Add this.

Goals:

* introduce a distinction between:

  * semantic action jobs
  * file-backed print jobs
* support job payload metadata for a selected print file
* validate accepted print file types
* support at least `.gcode` as first-class print asset
* reject non-sliced model files unless slicing support is explicitly added
* persist job-file association and operator-visible file metadata

Expected result:

* PrinterHub can represent a real print job as a file-backed runtime object
* “Print” page stops being a fake placeholder and starts representing actual printable work

This is the missing bridge between “job concept exists” and “piece can really be printed.”

### step E — Controlled print-start workflow for real printers

Add this.

Goals:

* introduce a compound workflow executor for print jobs
* define the ordered workflow:

  * validate printer readiness
  * lock printer ownership
  * optional homing/preparation
  * verify print asset availability
  * start print
  * transition to running
* persist step-by-step execution history
* prevent concurrent monitoring/command/job interference during critical phases

Expected result:

* a print job is no longer one raw action
* a real print can be started through a controlled runtime workflow

This is the actual “make a real print start” step.

### step F — Running print supervision and operator controls

Add this.

Goals:

* supervise running print jobs
* expose progress/status polling during print
* support pause/cancel as job-level controlled actions
* distinguish paused, cancelling, failed, completed terminal states
* persist terminal evidence and reason

Expected result:

* a real print is not only startable but operable and reviewable

Without this, “real print support” will still be half-finished.

---

# What should stay out of 0.2.3

This part is important.

## Do not force full slicing into 0.2.3 unless you explicitly want that scope explosion

Model import, slicing presets, G-code generation, thumbnail generation, and preview rendering are a different problem domain. Creality’s own flow treats those as slicer responsibilities before the printer-side send/print step. ([Creality Wiki][2])

So for `0.2.3`, the sane boundary is:

* **supported input for real print jobs: `.gcode`**
* **unsupported for now: STL/OBJ/3MF direct printing**
* maybe later: external slicer integration

That keeps `0.2.3` aligned with your Java runtime project instead of turning it into a full print-preparation suite.

---

# What the final subprocess map should look like

Here is the clean decomposition you should keep in mind.

```text
Real print workflow
├── 1. Print asset intake
│   ├── accept .gcode
│   ├── validate file
│   └── persist metadata
│
├── 2. Printer readiness
│   ├── enabled/reachable
│   ├── no conflicting execution
│   ├── state fresh enough
│   ├── optional homing
│   └── optional thermal/preparation checks
│
├── 3. Print-start orchestration
│   ├── bind asset to printer
│   ├── make file available
│   ├── start print
│   └── move job to RUNNING
│
├── 4. Running supervision
│   ├── progress polling
│   ├── pause/cancel
│   ├── anomaly detection
│   └── state transitions
│
└── 5. Audit and review
    ├── raw responses
    ├── classified outcomes
    ├── per-step diagnostics
    └── final completion/failure evidence
```

 