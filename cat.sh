{
  for f in \
    src/main/java/printerhub/monitoring/PrinterMonitoringTask.java \
    src/main/java/printerhub/monitoring/PrinterMonitoringScheduler.java \
    src/main/java/printerhub/api/RemoteApiServer.java \
    src/main/java/printerhub/persistence/PrinterSnapshotStore.java \
    src/main/java/printerhub/persistence/PrinterEventStore.java \
    src/main/java/printerhub/persistence/PrinterConfigurationStore.java \
    src/main/java/printerhub/runtime/PrinterHubRuntime.java \
    src/main/java/printerhub/runtime/PrinterRuntimeNode.java \
    src/main/java/printerhub/runtime/PrinterRuntimeStateCache.java \
    src/main/java/printerhub/SerialConnection.java \
    src/main/java/printerhub/serial/SimulatedPrinterPort.java
  do
    echo "===== $f ====="
    cat "$f"
    echo
  done
} > chat.txt
