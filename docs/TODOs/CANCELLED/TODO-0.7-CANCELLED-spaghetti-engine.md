# cancelled because will be part of the BenchChef project

## 0.7.2 — Performance and Accuracy Test Harness

### status 

planned

### Purpose

Create repeatable scripts and reports to measure deterministic engine behavior against the dataset.

### Scope

The harness should measure:

* disk usage
* memory usage
* CPU usage
* processing time
* throughput
* backend API response time
* detection accuracy
* false positives
* false negatives

The harness should support:

* single snapshot analysis
* single delta analysis
* batch dataset analysis
* simulated job-sequence analysis
* backend API analysis call
* longer-running load scenario

### Report Data

Each result row should include:

* test run ID
* dataset version
* input file
* input type
* expected result
* actual result
* pass/fail result
* engine name
* method name
* engine version
* parameters
* processing time
* detection score
* automatic motion/noise metrics
* generated debug output, if available

### Tooling Direction

Prefer open-source, portfolio-friendly tools:

* shell scripts for orchestration
* Rust CLI for engine execution
* Python for report generation, if useful
* `hyperfine` for command benchmarking
* `/usr/bin/time` or equivalent for process metrics
* CSV, JSON, Markdown, or HTML reports

### Acceptance Criteria

* A repeatable benchmark script exists.
* The script runs against the 0.7.0 dataset.
* Results are persisted in a structured format.
* Reports include both accuracy and technical metrics.
* Multiple engine/method variants can be compared.
* The harness can compare Java and external CLI engines through the same result model.

---

## 0.7.3 — Existing Engine Baseline

### Purpose

Run the existing deterministic engine against the dataset and establish the first measurable baseline.

### Scope

Use the 0.7.2 harness to evaluate the current engine.

The baseline should show:

* how often the existing engine detects spaghetti correctly
* how often it creates false positives
* how often it misses spaghetti
* whether false positives correlate with large global movement
* whether false negatives correlate with weak local spaghetti signal
* whether delta interval selection matters
* whether crop/region-of-interest improves detection

### Acceptance Criteria

* The existing engine has a documented baseline.
* At least one baseline report is archived.
* False positives and false negatives can be inspected.
* Reports include automatic motion/noise metrics.
* The baseline can later be compared with 0.9.x ML results.
