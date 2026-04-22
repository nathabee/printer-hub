# Test

## Structure and run

Maven uses the standard Java directory layout:

- `src/main/java` = production code
- `src/test/java` = test code

Production code:

- `Main.java` — CLI entry point
- `PrinterPoller.java` — polling workflow logic
- `PrinterPort.java` — communication interface
- `SerialConnection.java` — real serial hardware implementation

Test code:

- `FakePrinterPort.java` — fake printer used only for tests
- `PrinterPollerTest.java` — JUnit test class for polling behavior

Test code can use production code.  
Production code must not depend on test code.

### Run automated tests

```bash
mvn test
````

### Run real application

```bash
mvn exec:java
```

### Compile only

```bash
mvn clean compile
```

---

## Test matrix

| ID  | Scenario                 | Input / Setup                                  | Expected result                                            | Automated  |
| --- | ------------------------ | ---------------------------------------------- | ---------------------------------------------------------- | ---------- |
| T01 | Single poll success      | 1 poll, command `M105`, fake returns `ok ...`  | command sent once, disconnect called                       | Yes        |
| T02 | Multiple poll success    | 3 polls, fake returns `ok 1`, `ok 2`, `ok 3`   | command sent 3 times, disconnect called                    | Yes        |
| T03 | Connect returns false    | fake `connectResult = false`                   | `IOException` with connect failure                         | Yes        |
| T04 | Connect throws exception | fake `connectException = new IOException(...)` | `IOException`, disconnect called                           | Yes        |
| T05 | Null response            | fake returns `null`                            | `TimeoutException`, disconnect called                      | Yes        |
| T06 | Blank response           | fake returns blank string                      | `TimeoutException`, disconnect called                      | Yes        |
| T07 | Read throws exception    | fake `readException = new IOException(...)`    | `IOException`, disconnect called                           | Yes        |
| T08 | Send throws exception    | fake `sendException = new IOException(...)`    | `IOException`, disconnect called                           | Yes        |
| T09 | Invalid repeat count     | `repeatCount = 0`                              | `IllegalArgumentException`                                 | Yes        |
| T10 | Blank command            | command = blank                                | `IllegalArgumentException`                                 | Yes        |
| T11 | Real printer smoke test  | real `/dev/ttyUSB0`, real Ender printer        | connect, send `M105`, receive `ok ...`, disconnect cleanly | No, manual |

---

## Test coverage tool

Current automated tests verify the main polling logic and error paths in `PrinterPoller`.

Coverage is not yet measured as a percentage.
To measure code coverage, add a tool such as **JaCoCo** later.

Typical next step:

* add JaCoCo to `pom.xml`
* run `mvn test`
* open the generated HTML report in `target/site/jacoco/index.html`

Planned next stage:

* Jenkins CI
* automated `mvn test`
* optional publication of test and coverage reports

 