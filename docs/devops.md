# DevOps Overview

This document summarizes the current CI pipeline, its DevOps phase coverage, and the planned next steps.

Environment setup and Jenkins installation are described in:

* `install.md`

---

## Current Pipeline

The Jenkins pipeline currently performs:

```text
Checkout
→ Build
→ Test
→ Integration verification
→ Archive test reports
```

Core command:

```bash
mvn clean verify
```

This performs:

* compilation
* unit tests
* component tests
* integration tests
* JaCoCo coverage generation
* operator message report generation

---

## DevOps Phase Coverage

Current pipeline phase mapping:

| DevOps Phase       | Status  | Notes                                  |
| ------------------ | ------- | -------------------------------------- |
| **Checkout**       | Done    | Source pulled from Git                 |
| **Build**          | Done    | Java compilation via Maven             |
| **Test**           | Done    | Unit + component tests                 |
| **Integrate**      | Done    | Integration tests executed             |
| **Package**        | Partial | JAR created but not handled explicitly |
| **Release**        | Not yet | No release bundle                      |
| **Deploy**         | Not yet | No runtime deployment                  |
| **Verify runtime** | Not yet | No smoke execution                     |

Current classification:

```text
Continuous Integration (CI)
```

Not yet:

```text
Continuous Delivery (CD)
```

---

## Generated Artifacts

After a successful build:

```text
target/site/jacoco/index.html
target/operator-message-report.md
target/surefire-reports/*.xml
```

These artifacts provide:

* test validation evidence
* coverage visibility
* operator-facing message validation

---

## What Is Already Covered

The pipeline already includes:

* automated build verification
* multiple test levels
* integration-level testing
* coverage reporting
* operator-message validation
* archived CI evidence

This provides solid **CI validation**.

---

## Planned Next DevOps Steps

### Step 1 — Explicit Packaging

Goal:

Treat the application output as a release artifact.

Add:

```text
target/*.jar
```

to archived outputs.

---

### Step 2 — Simulated Runtime Execution

Goal:

Verify runtime behavior in CI without hardware.

Add:

```bash
mvn exec:java \
-Dexec.mainClass="printerhub.Main" \
-Dexec.args="SIM_PORT M105 3 100 sim"
```

This becomes:

```text
Smoke test stage
```

---

### Step 3 — Release Bundle Preparation

Goal:

Prepare a reproducible delivery package.

Example structure:

```text
release/
├── printer-hub.jar
├── jacoco/
├── operator-message-report.md
├── README.md
└── test.md
```

This supports:

* release validation
* traceability
* audit review

---

### Step 4 — Optional Deployment Stage

Future step:

Run the packaged application in a controlled environment.

Possible targets:

* simulated runtime environment
* container-based test environment
* staging system

---

## Target Pipeline Model

After improvements:

```text
Checkout
→ Build
→ Test
→ Integrate
→ Package
→ Simulated Deploy
→ Smoke Verify
→ Archive Release Bundle
```

Classification:

```text
Continuous Integration + Delivery Preparation
```

---

## DevOps Maturity Summary

Current state:

```text
CI: Build + Test + Integration
```

Next target:

```text
CI/CD: Build + Test + Package + Simulated Deploy
```

Long-term direction:

```text
Full delivery-ready pipeline
```
 