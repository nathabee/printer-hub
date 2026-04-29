# TODO

## Old code migration table for 0.1.x

| Old directory                              | Old file                                      | Likely content / responsibility                                                                        | New target                                                             | Version                                   |
| ------------------------------------------ | --------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------- | ----------------------------------------- |
| `old_src/main/java/printerhub`             | `PrinterPoller.java`                          | Main polling workflow: connect, send command, repeat polling, delay, disconnect, handle polling errors | `monitoring/PrinterMonitoringTask.java`                                | `0.1.1`                                   |
| `old_src/main/java/printerhub`             | `PrinterStateTracker.java`                    | Converts printer responses/errors into current printer state/snapshot                                  | `runtime/PrinterRuntimeStateCache.java` and maybe monitoring helper    | `0.1.1`                                   |
| `old_src/main/java/printerhub`             | `PrinterSnapshot.java`                        | Runtime snapshot model                                                                                 | `PrinterSnapshot.java`                                                 | `0.1.1`                                   |
| `old_src/main/java/printerhub`             | `PrinterState.java`                           | State enum                                                                                             | `PrinterState.java`                                                    | `0.1.1`                                   |
| `old_src/main/java/printerhub`             | `PrinterPort.java`                            | Printer communication interface                                                                        | `PrinterPort.java`                                                     | `0.1.1`                                   |
| `old_src/main/java/printerhub`             | `SerialConnection.java`                       | Real serial printer communication                                                                      | `serial/RealPrinterPort` or adapted serial layer                       | later `0.1.1` or `0.1.4`                  |
| `old_src/main/java/printerhub/serial`      | `SimulatedSerialPortAdapter.java`             | Simulated printer behavior                                                                             | `serial/SimulatedPrinterPort.java`                                     | `0.1.1`                                   |
| `old_src/main/java/printerhub/serial`      | `SimulationProfile.java`                      | Simulation modes: normal, timeout, error, disconnected                                                 | `serial/SimulatedPrinterPort.java` or helper                           | `0.1.1`                                   |
| `old_src/main/java/printerhub/serial`      | `SerialPortAdapter.java`                      | Serial abstraction                                                                                     | new serial layer                                                       | later                                     |
| `old_src/main/java/printerhub/serial`      | `JSerialCommPortAdapter.java`                 | jSerialComm implementation                                                                             | new serial layer                                                       | later                                     |
| `old_src/main/java/printerhub/serial`      | `SerialPortAdapterFactory.java`               | Selects real/simulated adapter                                                                         | runtime printer creation / registry loading                            | `0.1.3` or `0.2.x`                        |
| `old_src/main/java/printerhub`             | `RemoteApiServer.java`                        | Old HTTP API, dashboard endpoints, possibly direct polling                                             | `api/RemoteApiServer.java`                                             | `0.1.2`                                   |
| `old_src/main/resources/dashboard`         | `index.html`, `dashboard.css`, `dashboard.js` | Dashboard UI                                                                                           | resources/dashboard or API static handler                              | `0.1.2`                                   |
| `old_src/main/java/printerhub/farm`        | `PrinterFarmStore.java`                       | Old printer list/farm storage                                                                          | `runtime/PrinterRegistry.java` first, then `PrinterConfigurationStore` | `0.1.2` / `0.1.3`                         |
| `old_src/main/java/printerhub/farm`        | `PrinterNode.java`                            | Old printer node model                                                                                 | `runtime/PrinterRuntimeNode.java`                                      | already partly `0.1.0`, refine in `0.1.1` |
| `old_src/main/java/printerhub/persistence` | `Database.java`                               | SQLite connection handling                                                                             | persistence database layer                                             | `0.1.3`                                   |
| `old_src/main/java/printerhub/persistence` | `DatabaseConfig.java`                         | DB path/config                                                                                         | persistence database layer                                             | `0.1.3`                                   |
| `old_src/main/java/printerhub/persistence` | `DatabaseInitializer.java`                    | Table creation                                                                                         | `persistence/DatabaseInitializer.java`                                 | `0.1.3`                                   |
| `old_src/main/java/printerhub/persistence` | `RuntimeConfigurationStore.java`              | Persist printer config / runtime config                                                                | `PrinterConfigurationStore`                                            | `0.1.3`                                   |
| `old_src/main/java/printerhub/persistence` | `MonitoringRules.java`                        | Snapshot interval / threshold rules                                                                    | monitoring config / persistence store                                  | `0.1.3`                                   |
| `old_src/main/java/printerhub/persistence` | `PrinterSnapshotStore.java`                   | Persist snapshots                                                                                      | `PrinterSnapshotStore`                                                 | `0.1.3`                                   |
| `old_src/main/java/printerhub/persistence` | `PrinterEventStore.java`                      | Persist printer events/errors                                                                          | `PrinterEventStore`                                                    | `0.1.3`                                   |
| `old_src/main/java/printerhub/persistence` | `PrinterEvent.java`                           | Printer event model                                                                                    | event model/store                                                      | `0.1.3`                                   |
| `old_src/main/java/printerhub/jobs`        | all job files                                 | Job model, validation, execution, job storage                                                          | runtime job services/stores                                            | after `0.1.x`, probably `0.2.x`           |
| `old_src/main/java/printerhub`             | `OperationMessages.java`                      | Central operator-facing messages                                                                       | keep/adapt globally                                                    | as needed                                 |
| `old_src/main/java/printerhub`             | `Main.java`                                   | Old CLI/API startup wiring                                                                             | new `Main.java` / `PrinterHubRuntime` wiring                           | partly `0.1.0`, refine later              |
 



 ---


 ## 0.1.4


 Yes. For `0.1.4`, do it in this order:

```text
Step A — production code review / hardening
Step B — JUnit unit tests
Step C — Jenkins integration + robustness smoke tests
```

## Java files to review first

```text
src/main/java/printerhub/Main.java
src/main/java/printerhub/PrinterPort.java
src/main/java/printerhub/PrinterSnapshot.java
src/main/java/printerhub/PrinterState.java
src/main/java/printerhub/SerialConnection.java

src/main/java/printerhub/api/RemoteApiServer.java

src/main/java/printerhub/monitoring/PrinterMonitoringScheduler.java
src/main/java/printerhub/monitoring/PrinterMonitoringTask.java

src/main/java/printerhub/persistence/Database.java
src/main/java/printerhub/persistence/DatabaseConfig.java
src/main/java/printerhub/persistence/DatabaseInitializer.java
src/main/java/printerhub/persistence/MonitoringRules.java
src/main/java/printerhub/persistence/PrinterConfigurationStore.java
src/main/java/printerhub/persistence/PrinterEvent.java
src/main/java/printerhub/persistence/PrinterEventStore.java
src/main/java/printerhub/persistence/PrinterSnapshotStore.java

src/main/java/printerhub/runtime/PrinterHubRuntime.java
src/main/java/printerhub/runtime/PrinterRegistry.java
src/main/java/printerhub/runtime/PrinterRuntimeNode.java
src/main/java/printerhub/runtime/PrinterRuntimeNodeFactory.java
src/main/java/printerhub/runtime/PrinterRuntimeStateCache.java

src/main/java/printerhub/serial/JSerialCommPortAdapter.java
src/main/java/printerhub/serial/SerialPortAdapter.java
src/main/java/printerhub/serial/SimulatedPrinterPort.java
```

## Priority review points

Most important:

```text
RemoteApiServer
PrinterMonitoringTask
PrinterMonitoringScheduler
PrinterConfigurationStore
PrinterSnapshotStore
SerialConnection
PrinterHubRuntime
```

These decide whether the runtime is reliable.

## What to check before writing tests

### Error management

Check that errors are:

```text
caught per printer
stored in state cache
persisted as events
visible through API
not crashing scheduler
not blocking other printers
not hidden silently except during shutdown cleanup
```

### Monitoring priority

Expected behavior:

```text
disabled printer -> no polling
bad printer -> ERROR only for that printer
good printers -> keep updating
API reads cache only
database failure -> logged, but API/runtime still usable
```

### Persistence priority

Expected behavior:

```text
printer config survives restart
snapshot history is stored
events show cause of failure
database path can be overridden with -Dprinterhub.databaseFile
```

## JUnit tests to create

Suggested test files:

```text
src/test/java/printerhub/runtime/PrinterRegistryTest.java
src/test/java/printerhub/runtime/PrinterRuntimeStateCacheTest.java
src/test/java/printerhub/runtime/PrinterRuntimeNodeFactoryTest.java

src/test/java/printerhub/monitoring/PrinterMonitoringTaskTest.java
src/test/java/printerhub/monitoring/PrinterMonitoringSchedulerTest.java

src/test/java/printerhub/persistence/DatabaseInitializerTest.java
src/test/java/printerhub/persistence/PrinterConfigurationStoreTest.java
src/test/java/printerhub/persistence/PrinterSnapshotStoreTest.java
src/test/java/printerhub/persistence/PrinterEventStoreTest.java

src/test/java/printerhub/api/RemoteApiServerTest.java

src/test/java/printerhub/serial/SimulatedPrinterPortTest.java
src/test/java/printerhub/SerialConnectionTest.java
```

## Jenkins integration smoke tests

Keep one normal lifecycle:

```text
remove DB
start runtime
verify no printers
add simulated printer
verify monitoring
disable printer
verify updatedAt stops
enable printer
verify updatedAt resumes
update printer to sim-error
verify ERROR is visible
delete printer
verify removed
stop runtime
inspect SQLite
restart runtime
verify persisted printers reload
```

## Jenkins robustness smoke tests

Add focused failure scenarios:

```text
sim-error -> API still responds, event is persisted
sim-timeout -> API still responds, printer state is ERROR
sim-disconnected -> API still responds, other printers continue
invalid POST body -> HTTP 400
unknown printer -> HTTP 404
wrong method -> HTTP 405
```

## First files to paste for code review

Start with these:

```text
PrinterMonitoringTask.java
PrinterMonitoringScheduler.java
RemoteApiServer.java
PrinterSnapshotStore.java
PrinterConfigurationStore.java
```

Those are the highest-risk files before test writing.
