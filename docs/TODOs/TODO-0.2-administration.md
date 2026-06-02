## 0.2.x — Local Runtime Administration and Job Management

Goal:

* configurable runtime behavior
* clearer local administration
* controlled manual printer commands
* job lifecycle support
* operational history and diagnostics
* local service-style packaging

---

### 0.2.0 — Monitoring Configuration and Dashboard Administration Basics

status: done

Goals:

* expose monitoring rules through the API
* persist monitoring intervals and thresholds
* allow runtime tuning without code changes
* improve dashboard cards for local administration use
* show whether a printer is enabled or disabled directly on the card
* distinguish more clearly between:

  * disabled printer
  * disconnected/error printer
  * real printer
  * simulated printer

Endpoints:

```text
GET /settings/monitoring
PUT /settings/monitoring
```

Expected settings:

```text
poll interval
snapshot minimum interval
temperature delta threshold
event deduplication window
error persistence behavior
```

Dashboard expectations:

```text
show enabled / disabled status on each printer card
show real / simulated mode more clearly
make disabled state visually distinct from failure state
keep printer cards limited to configured printers
```

Expected result:

* monitoring behavior becomes configurable without source changes
* runtime tuning becomes persistent
* the dashboard becomes clearer for day-to-day local administration
* operators can immediately see whether a printer is intentionally disabled or operationally failing

---
 

### 0.2.1 — Manual Command Execution API

status: done

Goals:

* allow controlled manual printer commands
* execute commands through a dedicated command service
* keep manual command execution separate from monitoring
* persist command-related events
* support diagnostics, maintenance, and operator intervention
* start with a controlled predefined command set, not unrestricted raw command entry
* support single operator-triggered actions from the dashboard

Initial command scope:

```text
M105  read temperature
M114  read current position
M115  read firmware info
```

Possible later extensions:

```text
raw command input
movement commands beyond homing
pause/resume commands
restricted admin-only commands
```

Example endpoints:

```text
POST /printers/{id}/commands
GET  /printers/{id}/events
```

Dashboard expectations:

```text
predefined command buttons for safe read/info commands
small parameter forms for controlled commands such as target temperatures
single operator-triggered actions directly from the printer card
command result feedback visible in the dashboard
recent printer events visible for diagnostics
no direct free-text command box in the first step
```

Expected result:

* controlled operator commands become possible
* diagnostics are no longer limited to background monitoring
* monitoring and command execution remain separated internally
* dashboard-based single-command operator actions are available
* command handling creates the basis for later job execution services

---


### 0.2.2 — Job Management over Runtime Architecture
 
- step A : Foundation
- step B : Dashboard


status: done

Goals:

* connect print jobs to the runtime printer registry
* add persistent job creation and storage
* assign jobs to configured printers
* track job lifecycle through persisted state
* keep job logic out of HTTP handlers
* prepare later execution orchestration without coupling it directly to the API layer
* extend runtime nodes with execution ownership
* coordinate job execution with monitoring to avoid concurrent printer access
* expose basic job operations through the REST API
* make basic job creation and execution available through the dashboard

Expected lifecycle:

```text
CREATED
QUEUED
ASSIGNED
RUNNING
COMPLETED
FAILED
CANCELLED
```

Initial semantic job scope:

```text
READ_TEMPERATURE
READ_POSITION
READ_FIRMWARE_INFO
HOME_AXES
SET_NOZZLE_TEMPERATURE
SET_BED_TEMPERATURE
SET_FAN_SPEED
TURN_FAN_OFF
```

Job model note:

```text
A job is a first-class runtime object with its own lifecycle.
In this first implementation, one job maps to one guarded semantic printer action.
Manual commands from 0.2.1 remain operator-triggered actions outside the job lifecycle.
```

Expected result:

* jobs become a first-class runtime concept
* printer administration and job handling are connected
* persistence is ready for later execution logic
* basic job creation and execution are available through API and dashboard
* the runtime architecture is ready for richer execution and audit features later

 
---

</details>

### 0.2.3 — Local Audit, History Views, and Controlled Job Actions

status: done

* step A, B, C, D : done

#### step A — Audit and history visibility

status: done

Goals:

* expose printer event history
* expose snapshot history
* expose job history
* expose error history
* show job execution command and result details in dashboard and API
* make local diagnostics easier through both API and dashboard views
* make operator-triggered job outcomes reviewable after the fact

#### step B — New Dashboard UI and controlled real-printer job workflows

status: done

Goals:

* Dashboard UI with menu, navigation, component split to make it a two-level UI
* implement controlled job actions as predefined workflows, not just single raw command sends
* support multi-step preparation, validation, execution, and result interpretation for real-printer actions
* validate printer readiness before state-changing jobs are executed
* allow required pre-sequences before the main action command is sent
* make `HOME_AXES` a controlled workflow instead of only a direct `G28` send

Dashboard as two-level UI:

```text
PrinterHub
├── Farm Home
├── Printers
├── Jobs
├── History
└── Settings
```

Selected printer navigation:

```text
Selected Printer
├── Home
├── Print
├── Prepare
├── Control
└── Info
```
---

#### step C — Correct execution diagnostics and classified outcomes

status: done

Goals:

* persist the actual printer response that led to success or failure
* distinguish clearly between:

  * successful completion
  * printer-reported failure
  * timeout / no response
  * communication failure
  * validation failure before command execution
  * workflow/orchestration failure
* ensure printer-reported failures are not rewritten as generic “no response” failures when a response exists
* persist raw and/or normalized diagnostics in job history
* persist workflow-step diagnostics, not only final job state
* show sent command, actual response, classified outcome, and failure detail in dashboard history and API responses
* support review and cleanup of completed or failed jobs, including deletion of related job diagnostics/history where implemented

Controlled job-action scope:

```text
HOME_AXES
SET_NOZZLE_TEMPERATURE
SET_BED_TEMPERATURE
SET_FAN_SPEED
TURN_FAN_OFF
```

Note:

```text
For state-changing actions such as SET_NOZZLE_TEMPERATURE or SET_BED_TEMPERATURE,
step C classifies whether the guarded workflow command path succeeded or failed.
It does not yet prove that the requested physical target was reached and stabilized afterward.
```

Expected result:

* the local runtime becomes easier to inspect after failures
* dashboard and API become more useful for troubleshooting
* printer behavior, operator actions, and job state changes become reviewable after the fact
* controlled real-printer job actions become more operationally useful

---

#### step D — Asynchronous job execution

status : done

Goals: 

make POST /jobs/{id}/start return quickly, while the job runs in a background executor.

That step would include:

* add a bounded job executor pool
* keep one active job per printer
* return quickly from job start API
* use job state/events/diagnostics for progress
* keep dashboard behavior based on polling job state
* add tests for async start, busy printer rejection, and completed/failed background jobs


---

#### step E — File-backed print jobs and richer preparation/verification workflows

status: done

Goals:

* extend the job model so that jobs are no longer limited to one guarded semantic command
* support file-backed print jobs as a first-class runtime concept
* allow selection of an already prepared printable file stored on the PrinterHub host
* accept and validate printable file types, starting with `.gcode`
* persist print-file metadata and association with the job
* keep PrinterHub out of slicing logic:

  * no model slicing
  * no G-code editing
  * no slicer-host role in this version
* reject unsupported source formats for direct printing unless later explicitly integrated
* prepare richer controlled workflows where command acceptance and physical-effect verification are distinct concerns
* allow later workflow variants to include optional follow-up verification steps after state-changing commands


It will be done in this order:

- Add file metadata persistence. Done.
- Add `.gcode` validation and storage/registration logic. Done.
- Extend PrintJob so it can reference a print file. Done.
- Add backend API for printable files. Done.
- Add dashboard UI to select a file and create a file-backed job. Done.
- Keep actual G-code execution minimal/stubbed as “represented/prepared.” Done.
- Add tests around file validation, persistence, job creation, and unsupported file rejection. Done.


Job model note:

```text
A file-backed print job references an already prepared printable file.
PrinterHub does not generate or edit slice data in 0.2.x.
PrinterHub accepts an existing printable file, associates it with a job,
and later transfers or makes it available to the printer when execution starts.
```
 

Expected result:

* PrinterHub can represent a real print as a file-backed runtime job
* the Print area of the dashboard becomes tied to an actual printable artifact
* the runtime is prepared for real print activation using host-side stored files
* the job/workflow model is ready for richer verification-oriented actions beyond immediate command acceptance

---

Future local print execution mode split:

```text
Mode 1 — streamed job
PrinterHub owns the command stream.
PrintJobExecutionService sends G-code commands line by line,
waits for firmware acceptance, records diagnostics, and controls the full flow.

Mode 2 — autonomous printer job
PrinterHub stores or exposes a prepared file, requests print start,
monitors printer state, and persists telemetry/events while the printer firmware
owns the print execution.
```

0.2.3 continues with Mode 2 first because it is the safer first real-print path
for local PrinterHub operation. Mode 1 remains useful for mini jobs, calibration
jobs, controlled command sequences, and future streaming workflows, but it should
not block the first autonomous print activation milestone.

---


#### step F — SD-card administration and guarded host-side G-code upload

status: done

Purpose:

Make the selected-printer SD Card workflow real and usable:

```text
PrinterHub can inspect printer-side SD files,
register and manage printable targets,
store host-side .gcode files,
and copy a host-side file to a real printer SD card through
a guarded Marlin-style upload session.
```

This step does not yet start a real autonomous print from the uploaded file.

Goals:

* add a selected-printer SD Card view to inspect printer-side printable files
* move file preparation/upload responsibilities out of Print job creation and into the SD Card view
* make SD upload feedback visible in the dashboard with clear in-progress / success / failure status
* support Marlin-style host-driven SD upload only through a dedicated guarded upload session, not through the simple single-command path
* prevent conflicting access between monitoring, manual commands, and active SD upload execution
* persist SD-card listing, registration, enable/disable, and upload diagnostics

Selected-printer dashboard addition:

```text
Selected Printer
├── Home
├── Print
├── SD Card
├── Prepare
├── Control
├── Info
└── History
```

SD Card view scope:

* list printer-side files as reported by the firmware
* show the information available from the printer, such as:

  * filename
  * size when available
  * date/time when available
  * raw firmware line/details when richer parsing is not yet reliable
* refresh the SD-card file list on user demand
* automatically register newly discovered printer-side files when the SD-card list is refreshed, using the firmware-reported path as the printer-side path
* keep registration separate from print availability:

  * registered means PrinterHub knows that this printer-side path exists or existed
  * enabled means the file is allowed to appear in `PRINT_FILE` job creation
  * disabled registered files remain in persistence/history but are hidden from normal print-job selection
* register or upload a host-side `.gcode` file into the configured PrinterHub print-file storage directory
* show the relationship between host-side file metadata and printer-side firmware path when known
* allow review/edit of the host-side registered `.gcode` file before it is copied to the printer SD card, where safe
* upload/copy a registered host-side file to the printer SD card only when a reliable transfer path is confirmed
* show guarded upload status directly in the SD Card view
* after a host-side file is copied to a printer SD card, refresh/read that printer SD card so the resulting firmware path can be registered for that specific printer
* provide the registered printer-side file target that the Print page can later use for autonomous `PRINT_FILE` jobs

Print page scope after this split:

* create a print job from an enabled, already registered printer-side file target
* do not upload, edit, or register `.gcode` files directly in the job creation form
* show enough file identity for the operator to know what will be printed:

  * display name when available
  * firmware path / printer SD path
  * source host file when linked
  * size when available

Backend/API scope:

* add printer-specific SD-card/file-list operations
* add persistent mapping/registration for printer-side files discovered on SD card
* add enabled/disabled state to registered printer-side file targets and filter job creation to enabled targets
* add API support for registering an existing SD-card firmware path as a printable target
* add API support for enabling/disabling registered printer-side file targets
* keep host-side `.gcode` registration and upload APIs, but present them in the SD Card workflow rather than job creation
* add guarded API support for copying/transferring a registered host-side file to one selected printer SD card
* parse Marlin-style file-list responses as far as practical
* persist relevant file-operation diagnostics as printer events and/or job execution steps
* keep SD-card operations coordinated with monitoring and active jobs
* implement dedicated upload/session handling for firmware that requires numbered and checksummed host upload lines during `M28`/`M29` file transfer
* do not treat SD upload as a normal one-command request/response exchange

Suggested substeps:

1. Add firmware/file-list command support and parsing for SD-card listing. Done.
2. Add backend API for selected-printer SD-card file listing. Done.
3. Add the dashboard SD Card menu and read-only file list. Done.
4. Add automatic printer-side file registration for files discovered on SD-card refresh. Done.
5. Move host-side `.gcode` register/upload UI from Print job creation into the SD Card workflow. Done.
6. Persist/link host-side file metadata to printer-side firmware path when known. Done.
7. Change Print job creation so it only selects a registered printer-side printable target. Done.
8. Add enabled/disabled state for registered printer-side files and filter Print job creation to enabled targets. Done.
9. Add dashboard controls to enable/disable registered printer-side files. Done.
10. Add guarded host-file-to-printer-SD transfer entry points in backend and dashboard. Done.
11. Add dashboard-visible upload status and clearer operator error reporting for SD upload attempts. Done.
12. Real-printer verify the transfer path on the Ender-style Marlin path. Done.
13. Implement `SdCardUploadService` as a dedicated upload/session service instead of reusing the simple command path. Done.
14. Implement numbered/checksummed line streaming for SD upload, including resend handling and clean `M29` close. Done.
15. After transfer, refresh SD-card listing and link the discovered printer-side path to the host print file where possible. Done with confirmation-only linking.
16. Persist workflow steps and printer events for listing, registration, enable/disable, transfer, resend, confirmation, and failures. Done.
17. Add tests for list parsing, API responses, file registration/mapping, enable/disable filtering, upload-session behavior, checksum/resend handling, busy-printer rejection, and unsupported file operations. Done.

Expected result:

* the selected-printer dashboard can inspect printer-side SD-card files
* the SD Card page owns file preparation, host upload, SD-card registration, and guarded transfer actions
* the Print page creates jobs only from registered printer-side printable targets
* PrinterHub can copy a host-side `.gcode` file to the verified real printer SD card through a dedicated numbered/checksummed upload session
* upload confirmation depends on the printer SD listing, not on optimistic fallback registration
* Mode 2 print activation remains the next step

Note:

The upload path is currently verified and adapted against the Ender 2 Neo V3 style Marlin behavior. On that path, PrinterHub resets host line numbering, opens `M28` inside the numbered session, streams numbered/checksummed content lines, closes with numbered `M29`, and verifies with numbered `M20`.

---


#### step G — Autonomous real-printer print-start workflow and SD-card operation hardening

status: done

Purpose:

Turn the prepared SD-card/runtime model from Step F into actual autonomous
printer-side print activation, and finish the remaining SD-card administration
hardening.

Step G is about Mode 2:

```text
PrinterHub selects a registered printer-side file target,
asks the printer firmware to start printing it,
then observes the printer through monitoring, events, and job diagnostics.
```

Goals:

* implement Mode 2 controlled execution of file-backed `PRINT_FILE` jobs
* treat autonomous print start as a multi-step workflow, not as one direct command send
* validate printer readiness before print activation
* support required preparation phases before print start
* support printer-side file selection and activation as part of the job workflow
* add basic monitoring-assisted autonomous print completion handling after activation
* persist execution-step history for the print-start workflow
* add explicit guarded delete of printer-side SD files from the SD Card administration view
* track deleted printer-side files in persistence distinctly from enabled/disabled state
* add a monitoring/debug setting that enables printer wire trace logging only when operators request it
* verify and test upload behavior for `.gcode` files containing comments

Typical workflow scope:

```text
PRINT_FILE
├── validate printer enabled/reachable
├── validate no conflicting active job
├── validate fresh enough runtime state
├── optional prepare / homing / thermal checks
├── validate selected registered printer-side file target
├── inspect SD-card file state when needed
├── select firmware path / printer SD path
├── request autonomous print start
└── transition job to RUNNING
```

Suggested substeps:

1. Implement guarded printer-side file selection / print-start workflow.
2. Persist workflow steps and printer events for selection, activation, running transition, and failures.
3. Add explicit guarded delete only after real-printer command behavior is verified.
4. Extend printer-side file persistence beyond enable/disable so deleted files can remain traceable in history.
5. Add dashboard delete controls in the SD Card administration view.
6. Add a monitoring/debug flag that enables printer command/response trace logging only when requested.
7. Verify and test `.gcode` upload behavior with comment lines and representative real files.
8. Add tests for print-start workflow decisions, SD delete behavior, debug-trace flag behavior, and commented-file upload handling.
9. Add initial monitoring-assisted `RUNNING -> COMPLETED` handling for autonomous SD prints when firmware responses make completion observable.

Expected result:

* a real autonomous printer-side print can be started through the runtime as a controlled workflow
* file-backed print jobs are no longer just metadata
* print activation becomes coordinated, reviewable, and safer for real hardware use
* basic autonomous completion detection is available through printer monitoring for observable firmware responses
* SD-card administration covers enable/disable, delete, host upload, and operator-selected tracing
* Mode 1 streamed printing remains a later local-runtime capability

---


#### step H — Autonomous running print supervision and operator controls

status: done

Goals:

* deepen Mode 2 running real-printer supervision beyond the initial completion handling added in Step G
* expose running-print state and progress as far as the printer/firmware allows
* support controlled pause and cancel behavior for active print jobs
* distinguish clearly between:

  * running
  * paused
  * cancelling
  * completed
  * failed
  * cancelled
* persist terminal evidence and operator-visible outcome details
* improve dashboard visibility for active print execution

Already covered in Step G:

* autonomous print-start workflow events and execution-step diagnostics
* initial monitoring-assisted `RUNNING -> COMPLETED` handling when firmware responses expose completion

Expected result:

* autonomous real print jobs are not only startable but operable
* dashboard and API can follow running print execution more meaningfully
* completion, cancellation, and failure become properly reviewable in job history

---


#### step I — Dashboard print-job controls and recovery actions

status: done

Purpose:

Close the remaining operator-control gap in the dashboard so autonomous
printer-side jobs can be paused, resumed, cancelled, and restarted from a clear
browser workflow.

Goals:

* format dashboard timestamps for operators instead of showing raw ISO instants such as `2026-05-08T05:40:35.861517049Z`
* expose controlled pause and resume actions in the dashboard for active autonomous `PRINT_FILE` jobs
* add dedicated API routes for autonomous print pause and resume if they are not already exposed
* keep cancel behavior available for running and paused print jobs
* treat autonomous print cancel as a verified workflow: send abort, then confirm through SD print status before marking the job terminal
* prevent cancel from changing terminal jobs; `COMPLETED`, `FAILED`, and `CANCELLED` jobs must keep their final outcome
* add a cancel-request / waiting-for-printer-confirmation state when firmware reports busy or requires a physical printer-side stop confirmation
* add a restart/retry action for completed, failed, or cancelled `PRINT_FILE` jobs
* make restart create a new job attempt rather than mutating old completed history
* show which original job a restarted/retried job came from
* prevent restart when the original printer-side file target is deleted, disabled, or missing
* disable impossible controls based on current job state:

  * `RUNNING` can pause or cancel
  * `PAUSED` can resume or cancel
  * `COMPLETED`, `FAILED`, and `CANCELLED` can restart when the file target is still valid
  * `CREATED`, `QUEUED`, and `ASSIGNED` can start or cancel according to existing rules
* persist operator-control diagnostics for pause, resume, cancel, and restart
* expose job history and execution diagnostics consistently from the Print page job card and the selected-printer History page
* make job history clearly show:

  * pause command and response
  * resume command and response
  * cancel command and response
  * cancel status verification command and response
  * cancel-request / printer-busy evidence
  * restart source job
  * terminal outcome of each attempt
* make delete actions work from job cards where deletion is shown
* add filtering to the SD Card registered targets table by status, such as enabled, disabled, deleted, linked, and unlinked
* add an SD upload recovery action that can close an interrupted printer-side file-write session with a correctly numbered `M29`
* on SD upload failure after `M28` has opened the file, attempt the numbered `M29` close before reporting the upload as failed
* add a dashboard/API recovery command for operators when the printer remains in SD write mode after a failed transfer
* detect and represent USB-only versus mains-powered printer state when firmware exposes enough evidence
* gate dangerous or state-changing commands when the printer appears USB-powered only or otherwise not safely powered
* extend printer state beyond `IDLE` where useful, for example power-limited, waiting for confirmation, cancelling, and recovery-needed states
* add favicon/browser tab icon support for the dashboard
* improve dashboard wording so operators understand whether an action controls the printer firmware or only the PrinterHub job record

Dashboard expectations:

```text
Job card controls
├── Start    visible/enabled for ASSIGNED jobs
├── Pause    visible/enabled for RUNNING PRINT_FILE jobs
├── Resume   visible/enabled for PAUSED PRINT_FILE jobs
├── Cancel   visible/enabled for RUNNING or PAUSED jobs
└── Restart  visible/enabled for COMPLETED, FAILED, or CANCELLED PRINT_FILE jobs
```

```text
SD Card registered targets
├── filter by enabled / disabled
├── filter by deleted / available
├── filter by linked host file / unlinked printer-side path
└── keep upload/recovery status visible next to affected targets
```

API expectations:

```text
POST /jobs/{id}/pause
POST /jobs/{id}/resume
POST /jobs/{id}/cancel
POST /jobs/{id}/restart
POST /printers/{id}/sd-card/recovery/close-upload
```

Real-printer findings from Step I testing moved to `0.2.4`:

* Dashboard date/time values are now formatted for operators instead of raw ISO instants.
* Print page and global Jobs page job cards expose history and diagnostics consistently.
* Job-card delete controls are wired through the existing delete endpoint.
* `TURN_FAN_OFF` reports `M107 -> ok`, but the real printer fan continues running loudly.
* `SET_FAN_SPEED` with `M106 S0` reports `ok`, but the real printer fan sound does not change.
* Fan-control behavior needs hardware interpretation: distinguish controllable part-cooling fan from hotend, board, or power-supply fans that may not respond to `M106`/`M107`.
* Fan jobs currently prove command acceptance only; Step I should decide whether follow-up verification, clearer dashboard wording, or printer-specific capability notes are needed.
* `SET_NOZZLE_TEMPERATURE` and `SET_BED_TEMPERATURE` still need real-printer dashboard verification.
* Some commands work while the printer is USB-powered only, such as `M105`, but movement/heating/state-changing commands may be unsafe or firmware-hostile without mains power.
* Reliable mains-power detection is not exposed by the currently observed firmware response; later work should keep adding conservative warnings/gating as evidence becomes available.
* Failed or interrupted SD upload can leave the printer in an SD file-write session; Step I adds an operator recovery action that sends a numbered/checksummed `M29` close path.
* Cancel during autonomous print can receive repeated `busy` responses or mixed stale serial output; dashboard/backend now avoid terminal cancellation unless status verification confirms the print stopped.
* An `ok` after `M524` is not enough evidence by itself because stale serial responses can be mixed in; Step I cancellation verifies with `M27` before marking the job `CANCELLED`.
* Completed, failed, and cancelled jobs keep their terminal outcome and expose restart/retry instead of cancel.
* Dashboard includes a browser favicon/tab icon.

Expected result:

* operators can control an active autonomous print directly from the dashboard
* failed, cancelled, or completed print jobs can be retried without losing the original audit trail
* dashboard controls match job state instead of showing generic actions
* pause, resume, cancel, and restart are reviewable in job history and diagnostics
* dashboard tables and timestamps become practical for daily operator use
* real-printer anomalies are either fixed or represented honestly as firmware/hardware limitations

Expected result for 0.2.3 overall:

* audit and history views become useful for real diagnostics
* controlled printer-side actions become more robust and reviewable
* PrinterHub can manage a real print job based on an already prepared printable file
* the dashboard reflects both Ender-like printer logic and browser-native reviewability
* the runtime is ready for local real-printer print execution without becoming a slicer host

---


### 0.2.4 — Real-printer correction, SD upload hardening, and local packaging

Purpose:

Use the real-printer findings from `0.2.3` to close the remaining SD-upload and runtime-behavior gaps, then prepare the local runtime for stronger adaptive transfer behavior and later service-style packaging.

---

#### 0.2.4 — Step A — Real-printer correction and anomaly closure

status: done

Goals:

* harden completion detection for short autonomous SD prints so jobs do not remain stuck in `RUNNING` after the printer has already finished
* prevent stale active-job or stale printer-busy state from blocking restart or new print attempts when monitoring already shows the printer is idle
* improve cancel handling for printers that report `busy` or require physical confirmation before stop behavior is actually visible
* represent waiting and recovery states clearly in the dashboard, such as `CANCEL_REQUESTED`, `WAITING_FOR_PRINTER_STOP`, and recovery-needed situations
* keep cancellation evidence command-specific, so stale serial `ok` responses are not mistaken for proof that `M524` stopped the print
* strengthen SD upload recovery after interrupted `M28` write sessions
* verify `SET_NOZZLE_TEMPERATURE` and `SET_BED_TEMPERATURE` on the real printer with conservative values
* clarify whether temperature job success means command accepted, target trend observed, or target physically reached
* clarify observed fan-control behavior on the Ender-style printer, especially the distinction between controllable part-cooling fan and always-on hotend, board, or PSU fans
* detect or conservatively represent USB-only versus mains-powered state where firmware evidence allows
* gate or warn before dangerous movement, heating, or state-changing commands when safe printer power state is uncertain

Expected result:

* real-printer dashboard behavior matches observed firmware behavior
* stale `RUNNING` and stale busy states no longer block restart or new print attempts
* operators can distinguish command acceptance from verified physical effect
* the major real-printer anomalies discovered during `0.2.3` are either corrected or represented honestly as firmware or hardware limits

---

#### 0.2.4 — Step B — SD upload observability and transfer performance

status: done

Goals:

* add long SD upload progress reporting based on total upload lines and total file size known before transfer starts
* expose upload progress through backend state and dashboard UI
* show in-progress, success, and failure states clearly, including retry or resend evidence when relevant
* disable conflicting actions for the same printer while an SD upload is active, while keeping unrelated printers usable
* differentiate serial communication behavior between command-response operations and file-streaming operations
* reduce host-side polling overhead during SD transfer so upload throughput is not artificially limited by the runtime
* prepare the serial layer for later streamed print execution

Expected result:

* SD uploads are visible and reviewable during execution
* same-printer conflicting actions are blocked while upload is active
* command-response and file-streaming traffic use different serial timing behavior
* SD upload performance is improved where host-side wait behavior was part of the bottleneck
* transfer behavior is instrumented well enough to compare real-printer results at different timing and batching settings

---

#### 0.2.4 — Step C — Windowed SD upload

status: done

Goals:

* introduce a configurable SD upload batch-size setting representing the maximum number of numbered lines allowed in flight
* preserve the original per-line behavior when batch size is `1`
* allow real pipelined upload behavior when batch size is greater than `1`
* parse acknowledgement blocks correctly so multiple `ok` responses are not collapsed into a single logical response
* keep resend, upload error, and recovery-close behavior visible enough for diagnostics
* ensure `M29` closes the upload session using the correct next protocol line number rather than reusing the failed line number
* support small in-flight upload windows on real hardware and make performance testing measurable

Expected result:

* `batch=1` keeps the previous conservative behavior
* `batch>1` enables real multi-line in-flight SD transfer
* upload sequencing remains protocol-correct for numbered and checksummed lines
* failures during pipelined upload are diagnosable and recoverable
* the runtime can now measure whether small pipelined windows improve real-printer SD upload throughput

State-machine note:

```text
OPEN_UPLOAD
  -> M110 N0
  -> M28 target.gco
  -> upload file content in batches

For each batch:
  -> send whole batch in pipelined mode
  -> read responses one by one

  if all responses are ok:
      -> next batch

  if printer requests Resend: X:
      -> stop pipelined processing for this batch
      -> find X inside the current batch
      -> resend line X with normal retry logic
      -> resend X+1, X+2, ... to the end of that same batch, line-by-line
      -> when batch tail is fully accepted, continue with next batch in pipelined mode

  if X is not inside the active batch:
      -> fail upload
      -> optional recovery-close with M29
      -> stop

  if unrecoverable line error / retry exhaustion / connection failure:
      -> fail upload
      -> optional recovery-close with M29
      -> stop

After all file lines accepted:
  -> send M29
  -> list files with M20
  -> verify uploaded file exists
  -> success
```

---

#### 0.2.4 — Step D — buffered resend recovery and degraded replay stabilization

status: done

Goals:

* keep SD upload isolated from normal monitoring activity on the selected printer
* support pipelined SD upload with a configurable batch size
* retain a recent sent-line recovery history sized as `sdUploadBatchSize * sdUploadRecoveryWindowMultiplier`
* default `sdUploadRecoveryWindowMultiplier` to `2`
* allow resend recovery not only inside the active batch but also inside recently sent buffered history
* when resend targets a recoverable buffered line, replay line-by-line from that line through the end of buffered sent history
* drain pending serial input before resend replay starts
* switch the current upload into degraded single-send mode after resend instability is detected
* keep upload progress, protocol error counts, resend evidence, drain evidence, and recovery evidence visible in printer events
* fail only when recovery leaves the retained history window or when protocol instability exceeds configured limits

Expected result:

* SD upload can begin in pipelined mode for better throughput
* resend recovery works for both the current batch and recently buffered sent lines
* stale unread burst responses are drained before replay begins
* after a resend event, the upload can continue safely in degraded single-send mode instead of re-entering unstable batching immediately
* failures are diagnosable as resend outside recovery window, repeated identical resend loops, timeout, printer-side error, or cumulative protocol instability
* correctness and channel resynchronization now take priority over peak throughput once instability has been detected

Implemented behavior:

```text
OPEN_UPLOAD
  -> stop monitoring for this printer
  -> connect upload session
  -> send M110 N0
  -> send M28 target.gco
  -> initialize recent sent-line recovery history

NORMAL_UPLOAD
  -> build next outgoing window
  -> store window in recovery history
  -> send window in pipelined mode
  -> read responses one by one

if all responses are ok:
  -> continue with next unsent window

if printer requests Resend: X:
  -> if X is recoverable:
       drain pending serial input
       enable degraded single-send mode
       replay line-by-line from X through newest buffered sent line
       continue upload in single-send mode
  -> else:
       fail upload

if unrecoverable line error / retry exhaustion / connection failure:
  -> fail upload
  -> optional recovery-close with M29

if all file lines are accepted:
  -> send M29
  -> list files with M20
  -> verify uploaded file exists
  -> success
```

Note:

```text
Step D no longer assumes that a successful replay is enough evidence to return
immediately to full pipelined upload. Once resend instability is detected,
the current upload session degrades to single-send mode for safety.
```

---

 
#### 0.2.4 — Step E — transfer settings administration foundation

status: done

Goals:

* stop hardcoding SD upload transfer defaults as compile-time-only constants
* introduce persistent runtime-configurable serial transfer settings
* initialize defaults from `SerialDefaults` / protocol defaults only once
* allow editing these settings through dashboard settings
* expose transfer settings through API
* make later upload/runtime steps consume persisted settings instead of raw constants

Deliverables:

* transfer settings model and persistence store
* API endpoints to read and update transfer settings
* dashboard settings page support for transfer tuning
* runtime wiring so upload services read persisted settings

Expected result:

* monitoring-style settings management exists for serial/upload behavior too
* later adaptive logic can build on persisted operator-defined limits
* no need to change code just to tune upload behavior

---

#### 0.2.4 — Step F — Adaptive SD upload control

Status: done

Goal:

Make SD upload batch size adapt at runtime instead of staying fixed or permanently degraded after the first resend.

##### Core idea

Use a simple safe controller:

* start fast
* downgrade quickly on instability
* upgrade slowly after proven stability
* keep buffered resend recovery
* abort only when recovery is no longer safe or error limits are exceeded

##### Runtime state

* `configuredMaxBatchSize`
* `configuredMinBatchSize`
* `activeBatchSize`
* `acceptedLinesSinceLastResend`
* `recentResendCount`
* `recentRecoveryCount`
* `singleSendMode`

##### Tuning thresholds

* `stableLinesForUpgrade`
* `resendsBeforeDowngrade`
* `recoveryEventsBeforeSingleSend`

##### Algorithm

At upload start:

* `activeBatchSize = configuredMaxBatchSize`
* `acceptedLinesSinceLastResend = 0`
* `recentResendCount = 0`
* `recentRecoveryCount = 0`
* `singleSendMode = false`

On clean progress:

* add accepted lines to `acceptedLinesSinceLastResend`
* if stability reaches `stableLinesForUpgrade`:

  * increase `activeBatchSize` by 1
  * never exceed `configuredMaxBatchSize`
  * reset `acceptedLinesSinceLastResend`

On resend:

* drain pending input if needed
* run buffered replay recovery
* increment resend and recovery counters
* reset `acceptedLinesSinceLastResend = 0`

After resend:

* if resend count reaches `resendsBeforeDowngrade`:

  * reduce `activeBatchSize` by 1
  * never go below `configuredMinBatchSize`

If recovery keeps happening at minimum batch size:

* enable `singleSendMode`

On out-of-window resend:

* count it as a protocol anomaly
* count it against `rejectedLineCount`
* count it against `sdUploadMaxErrors`
* try resynchronization from the oldest recoverable buffered line

Abort only when:

* resend recovery falls outside retained history and cannot be resynchronized safely
* `sdUploadMaxErrors` is exceeded
* `sdUploadMaxConsecutiveIdenticalResends` is exceeded
* another hard protocol failure occurs

##### Deliverables

* adaptive batch controller in SD upload runtime state
* persisted settings for stability and downgrade thresholds
* upload events for upgrades, downgrades, resync, and single-send fallback
* API/dashboard exposure for active runtime upload metrics

##### Expected result

PrinterHub should recover from resend instability safely, climb back up after stable stretches, and settle near a practical throughput level for the real printer instead of staying permanently slow after one recovery.


---

#### 0.2.4 — Step G — Backend-to-frontend upload telemetry exposure

status: done

Goals:

* expose SD-card upload transfer telemetry through `GET /printers/{printerId}/sd-card/uploads/status`
* keep persistent `SerialTransferSettings` separate from per-upload adaptive runtime state
* publish runtime adaptive values such as configured batch limits, active batch size, resend pressure, recovery pressure, transport mode, and last adaptation reason/time
* render the returned fields in the SD-card dashboard view as raw readable telemetry for verification
* preserve final upload-session telemetry after success or error without writing runtime tuning values back to database settings

Expected result:

The browser can verify Step F adaptive upload behavior during and after an upload: progress, quality, throughput, active batch size, resend/recovery pressure, transport mode, and the last controller decision are visible from the SD-card page.

 
#### 0.2.4 — Step H — Functional two-card upload monitoring display

status: done

Goals:

* split upload monitoring into a normal operator card and a separate adaptive diagnostics card
* keep the upload status card focused on state, file, progress, lines, bytes, speed, timing, rejected lines, quality, and detail
* group adaptive diagnostics by current runtime decision, configured limits, and stability/resend pressure
* keep the display functional and readable without applying the Step I visual polish layer yet

Expected result:

The SD-card page shows upload progress as a clear operator summary while keeping adaptive controller internals available in a separate diagnostics card for verifying runtime behavior during long uploads.

 
#### 0.2.4 — Step I — Modern upload monitoring UX and operator-grade visualization

status: done

Goals:

* add a frontend transfer-health indicator for healthy, recovering, degraded, fallback, failed, complete, and idle states
* make upload progress, throughput, ETA, line/byte counters, resend count, and transfer quality readable at a glance
* add color-coded quality, resend, recovery, and stability pressure meters
* show adaptive controller decisions with a mode badge, active batch chip, configured range, and last adaptation reason/time
* keep runtime telemetry display near the SD-card upload workflow while leaving persistent transfer settings in settings

Expected result:

The SD-card page now reads like an operator monitoring panel: progress and alarms are visually prominent, adaptive changes are called out, and resend/recovery pressure can be interpreted without scanning raw logs.

 
#### 0.2.4 — Step J — Remote dashboard upload synchronization

status: done

Goals:

* add `Synchronize` and `Stop sync` controls beside the SD-card file refresh action
* reuse selected-printer upload-status polling so another browser can follow an upload started elsewhere
* poll only `GET /printers/{id}/sd-card/uploads/status`, without refreshing SD-card files or touching printer serial traffic
* keep the last visible upload card when synchronization is stopped
* show whether the selected printer is in live upload sync or manual-refresh mode

Expected result:

A second operator can open the dashboard from another PC, select the same printer, click `Synchronize`, and watch the existing upload telemetry card update live until they stop synchronization or leave the page.


---
### 0.2.5 — Global monitoring workspace and cross-printer runtime observability

status: done

Purpose:

Add a global Monitoring workspace for observing runtime activity across all configured printers without first selecting one printer.

Goals:

* add a global `Monitoring` dashboard menu entry
* add `GET /monitoring` as a backend runtime aggregation endpoint
* summarize configured, enabled, disabled, busy, and error printers
* show active/recent jobs across the local farm
* show active or last-known SD upload telemetry across printers
* expose adaptive upload diagnostics globally without replacing the selected-printer SD Card workflow
* provide follow/synchronize actions that jump from the global page to the focused selected-printer workspace

Expected result:

Operators can open one global Monitoring page to see farm runtime health, active jobs, and SD upload telemetry across printers, then follow a specific upload or job into the detailed printer page when they need deeper control.


### 0.2.6 — Runtime Recovery and Serial Device Robustness

status: planned

Goals:

* improve recovery after real USB disconnect/reconnect
* reduce problems caused by unstable `/dev/ttyUSB*` device names
* make real-printer administration more robust
* improve operator visibility for serial-port failures

Minor CR / anomalies:

* README banner and dashboard screenshot path currently points to `docs/assets/media-src/...`, not a final published-media location
* dashboard.js: editing a disabled printer will re-enable it unintentionally (`enabled: true` always set even on update)

Focus:

* keep automatic retry behavior for recoverable monitoring failures
* better distinguish between:

  * disconnected device
  * invalid configured port
  * temporary communication failure
* support or document use of stable serial paths such as:

```text
/dev/serial/by-id/...
```

* improve dashboard/API error clarity for real printer connection problems

* now the job synchronization is basic : It jumps to the printer Print page and refreshes monitoring, but it does not yet start a dedicated job-status poller because there is no separate job live poller like the upload poller. So we beed a full live job polling.

#### 0.2.6.A — Serial disconnect classification and recovery behavior

status: done

Goals:

* classify serial communication failures with structured `serialFailureType` values
* distinguish device path, permission, busy/disconnected, timeout, write, protocol, temporary, and unknown serial failures
* keep monitoring retry behavior intact after a classified failure
* expose the classification through printer status, printer info, selected-printer home, and global Monitoring runtime views
* preserve the existing human-readable error message and event history behavior

#### 0.2.6.B — Stable serial path support and operator guidance

status: done

Goals:

* allow real-printer `portName` values to use stable `/dev/serial/by-id/...` paths without rewriting the configured value
* expose serial path metadata through printer and global Monitoring API responses
* warn operators when a real Linux printer uses unstable `/dev/ttyUSB*` or `/dev/ttyACM*` names
* show configured port, path type, stability, and guidance in Settings, Info, and Monitoring views
* document stable serial path discovery in install, quickstart, and dashboard docs

#### 0.2.6.C — Safer printer update behavior

status: done

Goals:

* preserve the existing enabled/disabled state when editing printer display name, mode, or port
* keep enable/disable as an explicit operator action
* make the dashboard edit form remember which printer is being edited, even if the visible ID field changes before save
* verify the API preserves the existing enabled state when a PUT update omits `enabled`

#### 0.2.6.D — Full live job synchronization from Monitoring

status: done

Goals:

* add a selected-job synchronization poller that refreshes job state, history, and execution diagnostics
* let Monitoring job synchronization jump to Selected Printer / Print and start live job follow
* show live job sync state and a Stop sync control on the selected-printer Print page
* stop synchronization automatically when a job reaches `COMPLETED`, `FAILED`, or `CANCELLED`
* keep manual job controls and existing upload synchronization behavior unchanged




Expected result:

* real printers recover more reliably after reconnect scenarios
* operators can understand whether the failure is caused by cable disconnect, changed port path, or invalid configuration
* local runtime administration becomes safer for real hardware use

---

### 0.3.0 — Local Security, Roles, and Dangerous Action Guards

status: planned

Goals:

* distinguish read-only monitoring actions from state-changing printer actions
* protect dangerous operations behind explicit confirmation
* introduce local operator/admin role separation
* prevent accidental execution of risky commands from the dashboard
* define safety wording for heating, movement, SD delete, cancel, and streamed execution
* add audit entries for all operator-triggered state-changing actions
* prepare authentication boundaries before central VPS integration

Risky action groups:

```text
heating
movement
homing
fan control
SD delete
file upload/overwrite
print start
pause/resume/cancel
emergency stop
streamed G-code execution
raw command execution
```

Expected result:

* PrinterHub becomes safer for real hardware operation
* operator actions are traceable
* central VPS integration later has a clean local permission model

#### 0.3.0.A — Local role and permission model

status: done

Goals:

* define built-in local roles: `VIEWER`, `OPERATOR`, and `ADMIN`
* define explicit backend permissions for dashboard viewing, printer visibility, monitoring, job control, SD-card/file operations, command execution, runtime configuration, and security management
* provide built-in role profiles that map each role to its default permission set
* add a lightweight `AuthorizationService` that can answer and enforce permission checks before API endpoint guards are wired in later steps
* keep the first implementation local and persistence-free so Step B can store the profiles cleanly

#### 0.3.0.B — Persist local security settings and role profiles

status: done

Goals:

* persist local security settings in SQLite with `securityEnabled`, `defaultRole`, and dangerous-action confirmation behavior
* persist built-in role profiles with permission JSON for `VIEWER`, `OPERATOR`, and `ADMIN`
* seed built-in role profiles during database initialization without changing user-modified profile permissions
* expose local security defaults through `/settings/security`, `/security/profile`, and `/security/roles`
* surface the first local security settings card and role profile summary in the dashboard Settings page

#### 0.3.0.C — Backend authorization guard for API endpoints

status: done

Goals:

* enforce persisted local role permissions in backend API handlers when local security is enabled
* keep dashboard visibility as UX only by checking permissions before endpoint handlers mutate runtime state
* resolve endpoint permissions consistently for printer configuration, settings updates, jobs, SD-card actions, print files, security settings, and command execution
* reject forbidden direct API calls with `403` and a clear permission-denied message
* support a local `X-PrinterHub-Role` override for testing/admin tooling until full user authentication exists

#### 0.3.0.D — Dangerous action confirmation model

status: done

Goals:

* add a backend dangerous-action model for heating, movement, homing, SD delete, upload overwrite, print start, print cancel, recovery close, raw command, and future streamed G-code execution
* enforce explicit `confirmed: true` request acknowledgement when `requireDangerousActionConfirmation` is enabled
* reject missing acknowledgement with HTTP `428` and a structured `confirmation_required` response containing the required dangerous action group
* keep read-only commands such as `M105`, `M114`, and `M115` outside the confirmation flow
* add dashboard confirmations and confirmation payloads for existing risky controls: SD upload, SD target delete, recovery close, job start/cancel, and dangerous manual commands

#### 0.3.0.E — Dashboard role-aware controls

status: done

Goals:

* show the current local security mode and effective dashboard role in the navigation/settings context
* add frontend permission helpers that evaluate the persisted role profile permissions already exposed by the backend
* disable job, SD-card, printer configuration, command, settings, and security controls that the current local role cannot execute
* keep disabled controls visible with role/permission hints so operators understand why an action is unavailable
* keep backend authorization as the real security boundary while improving dashboard clarity before rejected API calls happen

#### 0.3.0.F — Audit events for authorized and rejected state-changing actions

status: done

Goals:

* persist operator audit entries for state-changing API requests in SQLite
* record the local actor, effective role, resolved permission, dangerous action group, action path, target, result, failure reason, and timestamp
* write accepted audit entries when authorization and confirmation guards allow a state-changing action
* write rejected audit entries when authorization or dangerous-action confirmation blocks a request
* expose recent audit entries through `/operator-audit` and surface them in Monitoring plus selected-printer History views


---

