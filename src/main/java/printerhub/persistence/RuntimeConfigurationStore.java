package printerhub.persistence;

import printerhub.farm.PrinterNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RuntimeConfigurationStore {

    private static final String DEFAULT_RULE_ID = "default";

    public void ensureDefaultPrinter(String portName, String mode) {
        if (hasConfiguredPrinters()) {
            return;
        }

        savePrinter(new PrinterNode(
                "printer-1",
                "Primary printer",
                portName,
                mode
        ));
    }

    public void ensureDefaultMonitoringRules() {
        String sql = """
                INSERT OR IGNORE INTO monitoring_rules (
                    id,
                    snapshot_on_state_change,
                    temperature_threshold,
                    min_interval_seconds,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, DEFAULT_RULE_ID);
            statement.setInt(2, 1);
            statement.setDouble(3, 1.0);
            statement.setLong(4, 30);
            statement.setString(5, LocalDateTime.now().toString());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize default monitoring rules", e);
        }
    }

    public List<PrinterNode> findEnabledPrinters() {
        String sql = """
                SELECT
                    id,
                    name,
                    port_name,
                    mode
                FROM configured_printers
                WHERE enabled = 1
                ORDER BY id;
                """;

        return loadPrinters(sql);
    }

    public List<PrinterNode> findAllPrinters() {
        String sql = """
                SELECT
                    id,
                    name,
                    port_name,
                    mode
                FROM configured_printers
                ORDER BY id;
                """;

        return loadPrinters(sql);
    }

    public void savePrinter(PrinterNode printerNode) {
        if (printerNode == null) {
            throw new IllegalArgumentException("printerNode must not be null");
        }

        String sql = """
                INSERT INTO configured_printers (
                    id,
                    name,
                    port_name,
                    mode,
                    enabled,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, 1, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    port_name = excluded.port_name,
                    mode = excluded.mode,
                    enabled = excluded.enabled,
                    updated_at = excluded.updated_at;
                """;

        String now = LocalDateTime.now().toString();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printerNode.getId());
            statement.setString(2, printerNode.getName());
            statement.setString(3, printerNode.getPortName());
            statement.setString(4, printerNode.getMode());
            statement.setString(5, now);
            statement.setString(6, now);

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save configured printer", e);
        }
    }

    public void enablePrinter(String printerId) {
        updatePrinterEnabled(printerId, true);
    }

    public void disablePrinter(String printerId) {
        updatePrinterEnabled(printerId, false);
    }

    public MonitoringRules loadMonitoringRules() {
        String sql = """
                SELECT
                    snapshot_on_state_change,
                    temperature_threshold,
                    min_interval_seconds
                FROM monitoring_rules
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, DEFAULT_RULE_ID);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new MonitoringRules(true, 1.0, 30);
                }

                return new MonitoringRules(
                        resultSet.getInt("snapshot_on_state_change") == 1,
                        resultSet.getDouble("temperature_threshold"),
                        resultSet.getLong("min_interval_seconds")
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load monitoring rules", e);
        }
    }

    public void saveMonitoringRules(MonitoringRules rules) {
        if (rules == null) {
            throw new IllegalArgumentException("monitoring rules must not be null");
        }

        String sql = """
                INSERT INTO monitoring_rules (
                    id,
                    snapshot_on_state_change,
                    temperature_threshold,
                    min_interval_seconds,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    snapshot_on_state_change = excluded.snapshot_on_state_change,
                    temperature_threshold = excluded.temperature_threshold,
                    min_interval_seconds = excluded.min_interval_seconds,
                    updated_at = excluded.updated_at;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, DEFAULT_RULE_ID);
            statement.setInt(2, rules.isSnapshotOnStateChange() ? 1 : 0);
            statement.setDouble(3, rules.getTemperatureThreshold());
            statement.setLong(4, rules.getMinIntervalSeconds());
            statement.setString(5, LocalDateTime.now().toString());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save monitoring rules", e);
        }
    }

    private List<PrinterNode> loadPrinters(String sql) {
        List<PrinterNode> printers = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                printers.add(new PrinterNode(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("port_name"),
                        resultSet.getString("mode")
                ));
            }

            return printers;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load configured printers", e);
        }
    }

    private void updatePrinterEnabled(String printerId, boolean enabled) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        String sql = """
                UPDATE configured_printers
                SET
                    enabled = ?,
                    updated_at = ?
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, enabled ? 1 : 0);
            statement.setString(2, LocalDateTime.now().toString());
            statement.setString(3, printerId.trim());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update configured printer", e);
        }
    }

    private boolean hasConfiguredPrinters() {
        String sql = """
                SELECT COUNT(*) AS printer_count
                FROM configured_printers
                WHERE enabled = 1;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            return resultSet.next() && resultSet.getInt("printer_count") > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check configured printers", e);
        }
    }
}