# Test

## Structure and run

Maven uses the standard layout:

- `src/main/java` — production code
- `src/test/java` — test code

Main production classes:

- `Main.java` — CLI entry point, input validation, usage, exit codes
- `PrinterPoller.java` — polling workflow
- `PrinterPort.java` — printer communication abstraction
- `SerialConnection.java` — serial communication implementation
- `OperationMessages.java` — operator-facing messages

Serial adapter classes in `printerhub.serial`:

- `SerialPortAdapter.java` — adapter abstraction
- `JSerialCommPortAdapter.java` — real serial-port adapter
- `SerialPortAdapterFactory.java` — selects the adapter by mode
- `SimulatedSerialPortAdapter.java` — runtime simulation adapter

Main test classes:

- `PrinterPollerTest.java` — polling workflow tests
- `SerialConnectionTest.java` — serial communication tests
- `MainIntegrationTest.java` — end-to-end flow tests
- `MainRobustnessTest.java` — invalid-input and defensive-path tests
- `TestReportWriter.java` — generates the operator-message report

Test helpers:

- `FakePrinterPort.java`
- `printerhub.serial.FakeSerialPortAdapter.java`

`SimulatedSerialPortAdapter` is in `src/main/java` because simulation is a supported runtime mode, not only a test helper.  
`FakePrinterPort` and `FakeSerialPortAdapter` remain in `src/test/java` because they are test-only utilities.

Test code may use production code. Production code must not depend on test code.

---

## Runtime modes

The application supports three modes:

| Mode | Adapter | Hardware | Typical command |
|---|---|---:|---|
| Real | `JSerialCommPortAdapter` | Yes | `mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="/dev/ttyUSB0 M105 3 2000 real"` |
| Simulated | `SimulatedSerialPortAdapter` | No | `mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"` |
| Test | `FakePrinterPort` / `FakeSerialPortAdapter` | No | `mvn test` |

Argument order for `Main`:

```text
<port> <command> <repeatCount> <delayMs> [mode]
```

Examples:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="/dev/ttyUSB0 M105 3 2000 real"
mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"
mvn test
```

---

## Run commands

### Run automated tests

```bash
mvn test
```

### Run real application

```bash
mvn exec:java
```

### Compile only

```bash
mvn clean compile
```

### Run coverage and reports

```bash
mvn clean verify
```

### Inspect coverage report

```bash
xdg-open target/site/jacoco/index.html
```

### Inspect operator message report

```bash
xdg-open target/operator-message-report.md
```

### Optional XML formatting helper for JaCoCo

```bash
sudo apt install libxml2-utils
xmllint --format target/site/jacoco/jacoco.xml > jacoco-pretty.xml
```

### Optional grep on formatted JaCoCo XML

```bash
grep -n "printerhub/Main\|printerhub/SerialConnection\|printerhub/PrinterPoller" jacoco-pretty.xml
```

---

## Test scope

### `PrinterPollerTest`

Checks polling workflow in isolation.

Flow:

```text
PrinterPollerTest
-> PrinterPoller.runPolling(...)
-> FakePrinterPort
```

Focus:

* repeat and command validation
* loop and retry behavior
* timeout handling
* disconnect behavior
* workflow-level operator messages

---

### `SerialConnectionTest`

Checks serial communication behavior in isolation.

Flow:

```text
SerialConnectionTest
-> SerialConnection
-> FakeSerialPortAdapter
```

Focus:

* constructor validation
* connect, send, read, disconnect
* open/read/write/close failures
* low-level operator messages

---

### `MainIntegrationTest`

Checks the realistic application path without real hardware.

Flow:

```text
MainIntegrationTest
-> Main.run(...)
-> SerialPortAdapterFactory.create(...)
-> SimulatedSerialPortAdapter
-> SerialConnection
-> PrinterPoller
```

Focus:

* successful simulated run
* connect failure exit-code mapping
* timeout exit-code mapping
* top-level stdout/stderr behavior

This is the closest automated test to the real application flow.

---

### `MainRobustnessTest`

Checks invalid input and defensive branches at top level.

Flow:

```text
MainRobustnessTest
-> Main.run(...)
-> parse/validate
-> error handling / exit-code mapping
```

Focus:

* invalid numeric input
* invalid mode
* invalid init-delay property
* interrupted execution
* top-level error wording and exit codes

---

## Generated artifacts

Running tests or verification generates:

* `target/site/jacoco/index.html`
* `target/operator-message-report.md`

### JaCoCo report

Use the JaCoCo report to check:

* which classes are covered
* which branches remain untested
* whether new code introduced uncovered paths

Coverage is useful, but it does not replace message review.

### Operator message report

`target/operator-message-report.md` captures selected runtime-visible scenarios for operator review.

For each scenario, check:

* is the exit code correct
* is the output on the right stream
* is the wording understandable
* does the message say what failed
* does it contain enough context for action or escalation

---

## Release review

It is useful to keep the generated operator message report per release.

Example:

```bash
cp target/operator-message-report.md docs/reports/operator-message-report-0.0.5.md
cp target/operator-message-report.md docs/reports/operator-message-report-0.0.6.md
```

Compare releases with:

```bash
diff -u docs/reports/operator-message-report-0.0.5.md docs/reports/operator-message-report-0.0.6.md
```

This helps detect wording regressions, exit-code drift, and loss of useful operator context.

---

## Summary

The test strategy covers four things:

1. polling logic
2. serial communication behavior
3. full application flow
4. top-level robustness and operator-visible messages

That gives better confidence than coverage alone.
 