package printerhub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class TestReportWriter {

    private static final Path REPORT_PATH = Path.of("target", "operator-message-report.md");
    private static boolean initialized = false;

    private TestReportWriter() {
    }

    static synchronized void appendScenario(String scenarioName,
                                            int exitCode,
                                            String stdout,
                                            String stderr) throws IOException {
        initializeIfNeeded();

        StringBuilder block = new StringBuilder();
        block.append("## Scenario: ").append(scenarioName).append(System.lineSeparator()).append(System.lineSeparator());
        block.append("- Exit code: ").append(exitCode).append(System.lineSeparator());
        block.append("- Stdout:").append(System.lineSeparator());
        block.append(indentBlock(normalize(stdout))).append(System.lineSeparator());
        block.append("- Stderr:").append(System.lineSeparator());
        block.append(indentBlock(normalize(stderr))).append(System.lineSeparator());
        block.append(System.lineSeparator());

        Files.writeString(
                REPORT_PATH,
                block.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        );
    }

    private static void initializeIfNeeded() throws IOException {
        if (initialized) {
            return;
        }

        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(
                REPORT_PATH,
                "# Operator message report" + System.lineSeparator() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
        initialized = true;
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "  empty";
        }
        return text.stripTrailing();
    }

    private static String indentBlock(String text) {
        String[] lines = text.split("\\R", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append("  ").append(line).append(System.lineSeparator());
        }
        return out.toString().stripTrailing();
    }
}