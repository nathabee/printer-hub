# Dashboard


I checked the Ender-3 V2 Neo style screen/menu, which matches the Creality color-knob interface discussed earlier. The visible top-level screen shows a very compact production-oriented structure with **Print**, **Prepare**, **Control**, and **Info** on the home screen, plus always-visible machine status such as temperatures and axis values. The manual also explicitly references paths such as **Prepare → Auto home**, **Prepare → Move → Move Z**, **Prepare → Z-offset**, **Prepare → Disable stepper**, and **Control → Reset configuration**. ([Filament2Print][1])

That means the printer UI logic is not “monitoring first.” It is really:

* machine home/status,
* print-oriented operations,
* preparation actions,
* control/configuration,
* information.

That is exactly the logic you want to preserve in PrinterHub. ([Filament2Print][1])

## The key architectural decision

I would **not** make the whole web app look like one giant Ender screen.

I would make it a **two-level UI**:

```text id="o58pb2"
PrinterHub
├── Farm Home
├── Printers
├── Jobs
├── History
└── Settings
```

But once a printer is selected, its internal navigation should follow the Ender logic very closely:

```text id="j8m7ms"
Selected Printer
├── Home
├── Print
├── Prepare
├── Control
└── Info
```

That way:

* the **global shell** solves the multi-printer / browser problem,
* the **selected printer shell** keeps the exact machine logic of the Ender display.

That is the clean compromise.

## What the actual Ender menu suggests for your dashboard

From the Ender V2 Neo UI material, the relevant top-level logic is:

```text id="7pa8wy"
Home
├── Print
├── Prepare
├── Control
└── Info
```

and inside **Prepare**, the manual clearly shows at least:

```text id="zx5v6n"
Prepare
├── Auto home
├── Move
│   └── Move Z
├── Z-offset
└── Disable stepper
```

and inside **Control**, the manual explicitly shows:

```text id="ipn0nj"
Control
└── Reset configuration
```

The home screen also visibly keeps status values on screen, including nozzle/bed temperature, fan percentage, and position/status data. ([Filament2Print][1])

So for PrinterHub, your per-printer UI should mirror that structure.

# Proposed new UI structure

## A. Global application structure

```text id="zjmo1z"
PrinterHub
├── Farm Home
│   ├── Fleet summary
│   ├── Printer cards
│   ├── Recent alerts
│   └── Recent activity
│
├── Printers
│   ├── Printer list
│   └── Selected printer
│       ├── Home
│       ├── Print
│       ├── Prepare
│       ├── Control
│       ├── Info
│       └── History
│
├── Jobs
│   ├── All jobs
│   ├── Job detail
│   ├── Job lifecycle
│   └── Placeholder: production piece workflow
│
├── History
│   ├── Printer events
│   ├── Job events
│   ├── Placeholder: snapshots
│   ├── Placeholder: command results
│   └── Placeholder: error history
│
└── Settings
    ├── Monitoring rules
    ├── Printer administration
    └── Placeholder: general runtime settings
```

## Why this is the right split

Because the real Ender display is built for **one physical printer**. Your web app must cover:

* the farm,
* the printer,
* the job,
* the history,
* administration.

So the Ender logic should dominate the **selected printer area**, not the whole product.

# Per-printer UI, aligned to Ender logic

This is the important part.

## 1. Home

This should be the printer’s browser equivalent of the Ender home screen.

Show:

* printer identity
* enabled / disabled
* real / simulated
* current state
* nozzle temp actual / target
* bed temp actual / target
* current job summary
* last response
* current error
* quick actions
* last update timestamp

This page should feel immediately familiar to the Ender UI idea because the printer screen itself keeps live machine status at the front. ([Filament2Print][1])

## 2. Print

This is where your **job** concept belongs.

The Ender screen’s **Print** area is piece-oriented: choose a model, confirm it, print it, see preview/status. The V2 Neo UI material explicitly shows model preview and file selection as part of the print workflow. ([Filament2Print][1])

So your PrinterHub **Print** page should be the future-facing page for the evolving job model:

```text id="awk4zh"
Print
├── Active job
├── Assigned jobs
├── Job queue
├── Job parameters
├── Start / cancel / retry
├── Placeholder: model file
├── Placeholder: sliced plan preview
├── Placeholder: piece metadata
└── Placeholder: production progress
```

This is where you prepare the future transition:

* today: backend “job” is still a controlled embryo,
* tomorrow: backend “job” becomes the full piece-production workflow.

So do not rename it away. Keep it **Job**, but present it as a growing production object.

## 3. Prepare

This should match the machine-preparation logic from the printer display much more directly.

Because the manual explicitly places setup-style actions under **Prepare**, such as Auto Home, Move Z, Z-offset, and Disable Stepper. ([Manuals+][2])

Your dashboard’s **Prepare** page should therefore contain:

```text id="o4y0f6"
Prepare
├── Auto home
├── Move axis
├── Move Z
├── Z-offset
├── Disable steppers
├── Placeholder: preheat presets
├── Placeholder: load filament
├── Placeholder: unload filament
└── Placeholder: bed leveling helpers
```

This page is not about fleet monitoring. It is about machine preparation before or around a print.

## 4. Control

This is where settings and machine control live.

The manual explicitly references **Control → Reset configuration**, so Control is the right bucket for configuration-oriented printer-side actions. ([Manuals+][2])

Your Control page should contain:

```text id="n3nccs"
Control
├── Reset configuration
├── Manual command execution
├── Fan control
├── Temperature setpoints
├── Placeholder: motion tuning
├── Placeholder: flow/speed tuning
├── Placeholder: EEPROM/config sync
└── Placeholder: printer-side configuration controls
```

This is also where your current `POST /printers/{id}/commands` feature belongs.

## 5. Info

The Ender top-level home screen includes **Info**, and that maps well to a read-only technical page in PrinterHub. ([Filament2Print][1])

Use it for:

```text id="ymillc"
Info
├── Printer identity
├── Port
├── Mode
├── Firmware info
├── Capabilities
├── Last response
├── Current error
├── Placeholder: hardware profile
├── Placeholder: consumables/maintenance notes
└── Placeholder: piece/job reporting summary
```

This is where `M115`-style firmware information fits conceptually.

## 6. History

This is not visible as one of the Ender’s four main tiles, but the browser needs it.

So I would keep it as an extra browser-native tab under the selected printer.

```text id="nbrrj9"
History
├── Printer events
├── Job events
├── Placeholder: snapshot history
├── Placeholder: command result history
└── Placeholder: error history
```

That keeps the Ender logic intact while adding the web-only strengths.

# UI page to backend/status table

Below is the practical mapping using what your current frontend code proves already exists.

| UI page                                                 | Backend/API                                                  | Status                  |
| ------------------------------------------------------- | ------------------------------------------------------------ | ----------------------- |
| Farm Home / Fleet summary                               | `GET /printers`                                              | Available               |
| Farm Home / Recent alerts                               | derive from printer state/error in `GET /printers`           | Available but limited   |
| Farm Home / Recent activity                             | no clear aggregated endpoint yet                             | Placeholder             |
| Selected printer / Home                                 | `GET /printers`, `GET /printers/{id}/events`                 | Available               |
| Selected printer / Print / Job list                     | `GET /jobs` filtered by printerId                            | Available but embryonic |
| Selected printer / Print / Create job                   | `POST /jobs`                                                 | Available               |
| Selected printer / Print / Start job                    | `POST /jobs/{id}/start`                                      | Available               |
| Selected printer / Print / Cancel job                   | `POST /jobs/{id}/cancel`                                     | Available               |
| Selected printer / Print / Job events                   | `GET /jobs/{id}/events`                                      | Available               |
| Selected printer / Prepare / Auto home etc.             | partly via command execution API                             | Partial                 |
| Selected printer / Prepare / guided preparation actions | no dedicated high-level API set confirmed                    | Placeholder / partial   |
| Selected printer / Control / manual command execution   | `POST /printers/{id}/commands`                               | Available               |
| Selected printer / Control / printer config reset etc.  | not clearly exposed as dedicated endpoint                    | Placeholder / partial   |
| Selected printer / Info / technical summary             | printer data from `GET /printers`; firmware via command flow | Partial                 |
| Selected printer / History / printer events             | `GET /printers/{id}/events`                                  | Available               |
| Selected printer / History / job events                 | `GET /jobs/{id}/events`                                      | Available               |
| Selected printer / History / snapshots                  | no confirmed endpoint in current UI                          | Placeholder             |
| Selected printer / History / command results            | no dedicated endpoint in current UI                          | Placeholder             |
| Settings / Monitoring rules                             | `GET /settings/monitoring`, `PUT /settings/monitoring`       | Available               |
| Settings / Printer administration                       | printer CRUD + enable/disable                                | Available               |

# What this means for your current 0.2.3 UI redesign

You do **not** need to wait for the final job backend to make the UI meaningful.

You can already build a UI that says:

* **Print** = current and future job area
* **Prepare** = printer setup actions
* **Control** = machine control and low-level adjustments
* **Info** = technical information
* **Home** = live machine overview

That already feels like the Ender logic, and it leaves room for the job model to grow into the piece-production workflow later. The Ender V2 Neo interface itself is clearly centered on printing pieces, preparation, control, and info, not on a generic admin page, so this direction is the right one. ([Filament2Print][1])

# Frontend file reorganization

Yes, I would split the dashboard into a few files now.

Not too many. Just enough to match the new navigation.

## Suggested frontend structure

```text id="2ftcau"
src/main/resources/dashboard/
├── index.html
├── dashboard.css
├── app.js
├── api.js
├── state.js
├── views/
│   ├── farm-home.js
│   ├── printer-home.js
│   ├── printer-print.js
│   ├── printer-prepare.js
│   ├── printer-control.js
│   ├── printer-info.js
│   ├── printer-history.js
│   ├── jobs.js
│   └── settings.js
└── components/
    ├── nav.js
    ├── printer-card.js
    ├── job-card.js
    ├── event-list.js
    ├── status-panels.js
    └── placeholder-card.js
```

## Why this split is correct

Because your selected-printer area is now conceptually modeled after the Ender menu tree. So the file split should reflect that.

* `printer-home.js`
* `printer-print.js`
* `printer-prepare.js`
* `printer-control.js`
* `printer-info.js`

That is a sane split. Not excessive. Not messy.

## Backend naming consistency

And to your point: yes, if backend says `job`, frontend should say `job`.

The right approach is not to rename it away. The right approach is to **frame it correctly in the UI**:

* “Jobs” page
* “Print” page shows jobs
* placeholders make clear that the job model will evolve toward full piece-production workflow

That keeps terminology consistent while still making the future visible.

# My concrete recommendation

I would shape the selected printer UI like this:

```text id="67xsk6"
Selected Printer
├── Home
│   ├── Live status
│   ├── Temperatures
│   ├── Current job
│   ├── Last response
│   └── Quick actions
│
├── Print
│   ├── Jobs
│   ├── Start / cancel
│   ├── Job events
│   ├── Placeholder: file/model preview
│   └── Placeholder: piece production workflow
│
├── Prepare
│   ├── Auto home
│   ├── Move
│   ├── Z-offset
│   ├── Disable steppers
│   ├── Placeholder: filament handling
│   └── Placeholder: bed leveling helpers
│
├── Control
│   ├── Manual commands
│   ├── Temperature/fan controls
│   ├── Placeholder: reset configuration
│   └── Placeholder: tuning/config controls
│
├── Info
│   ├── Firmware / identity
│   ├── Port / mode
│   ├── Current errors
│   └── Placeholder: hardware profile
│
└── History
    ├── Printer events
    ├── Job events
    ├── Placeholder: snapshots
    └── Placeholder: command results
```

That is the cleanest way to stay aligned with:

* backend terminology,
* Ender display logic,
* future job evolution.
 