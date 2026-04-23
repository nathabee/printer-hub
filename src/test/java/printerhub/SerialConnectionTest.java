package printerhub;

import org.junit.jupiter.api.Test;
import printerhub.serial.SerialPortAdapter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static org.junit.jupiter.api.Assertions.*;

class SerialConnectionTest {

    private static final class TestSerialPortAdapter implements SerialPortAdapter {
        boolean openResult = true;
        boolean closeResult = true;
        boolean open;
        String systemPortName = "FAKE_PORT";

        IOException inputStreamFailure;
        IOException outputStreamFailure;
        IOException availableFailure;
        IOException writeFailure;
        IOException flushFailure;
        IOException closeInputFailure;
        IOException closeOutputFailure;

        InputStream inputStream;
        OutputStream outputStream;

        int baudRate;
        int numDataBits;
        int numStopBits;
        int parity;
        int timeoutMode;

        final ByteArrayOutputStream writtenBytes = new ByteArrayOutputStream();

        TestSerialPortAdapter() {
            setInputData("");
            this.outputStream = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    if (writeFailure != null) {
                        throw writeFailure;
                    }
                    writtenBytes.write(b);
                }

                @Override
                public void flush() throws IOException {
                    if (flushFailure != null) {
                        throw flushFailure;
                    }
                }

                @Override
                public void close() throws IOException {
                    if (closeOutputFailure != null) {
                        throw closeOutputFailure;
                    }
                }
            };
        }

        void setInputData(String data) {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            this.inputStream = new InputStream() {
                private final ByteArrayInputStream delegate = new ByteArrayInputStream(bytes);

                @Override
                public int available() throws IOException {
                    if (availableFailure != null) {
                        throw availableFailure;
                    }
                    return delegate.available();
                }

                @Override
                public int read() throws IOException {
                    return delegate.read();
                }

                @Override
                public void close() throws IOException {
                    if (closeInputFailure != null) {
                        throw closeInputFailure;
                    }
                }
            };
        }

        String getWrittenData() {
            return writtenBytes.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void setBaudRate(int baudRate) {
            this.baudRate = baudRate;
        }

        @Override
        public void setNumDataBits(int numDataBits) {
            this.numDataBits = numDataBits;
        }

        @Override
        public void setNumStopBits(int numStopBits) {
            this.numStopBits = numStopBits;
        }

        @Override
        public void setParity(int parity) {
            this.parity = parity;
        }

        @Override
        public void setComPortTimeouts(int mode, int readTimeout, int writeTimeout) {
            this.timeoutMode = mode;
        }

        @Override
        public boolean openPort() {
            open = openResult;
            return openResult;
        }

        @Override
        public boolean closePort() {
            open = false;
            return closeResult;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public InputStream getInputStream() {
            if (inputStreamFailure != null) {
                throw new RuntimeException(inputStreamFailure);
            }
            return inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            if (outputStreamFailure != null) {
                throw new RuntimeException(outputStreamFailure);
            }
            return outputStream;
        }

        @Override
        public String getSystemPortName() {
            return systemPortName;
        }
    }

    @Test
    void constructor_blankPortName_throws() throws Exception {
        captureAndReportIllegalArgumentScenario(
                "serial constructor blank portName",
                () -> new SerialConnection("   ", 115200),
                "portName must not be blank"
        );
    }

    @Test
    void constructor_nullPortName_throws() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();

        captureAndReportIllegalArgumentScenario(
                "serial constructor null portName",
                () -> new SerialConnection(null, 115200, adapter),
                "portName must not be blank"
        );
    }

    @Test
    void constructor_nullAdapter_throws() throws Exception {
        captureAndReportIllegalArgumentScenario(
                "serial constructor null adapter",
                () -> new SerialConnection("/dev/ttyUSB0", 115200, null),
                "portAdapter must not be null"
        );
    }

    @Test
    void constructor_twoArg_validPath_setsPortName() {
        SerialConnection connection = new SerialConnection("/dev/ttyUSB0", 115200);

        assertEquals("/dev/ttyUSB0", connection.getPortName());
    }

    @Test
    void connect_success_initializesStreamsAndMarksConnected() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok\n");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);

        boolean result = connection.connect();

        assertTrue(result);
        assertTrue(connection.isConnected());
        assertEquals(115200, adapter.baudRate);
        assertEquals(8, adapter.numDataBits);
        assertEquals(SerialPortAdapter.ONE_STOP_BIT, adapter.numStopBits);
        assertEquals(SerialPortAdapter.NO_PARITY, adapter.parity);
        assertEquals(SerialPortAdapter.TIMEOUT_NONBLOCKING, adapter.timeoutMode);
        assertEquals("FAKE_PORT", connection.getPortName());
    }

    @Test
    void connect_openFails_throwsIOException() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.openResult = false;

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);

        captureAndReportIOExceptionScenario(
                "serial connect failed to open port",
                connection::connect,
                "Failed to open serial port 'FAKE_PORT'"
        );

        assertFalse(connection.isConnected());
    }

    @Test
    void connect_whenAlreadyConnected_throwsIllegalStateException() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok\n");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        captureAndReportIllegalStateScenario(
                "serial connect when already connected",
                connection::connect,
                "Connection already open on port FAKE_PORT"
        );
    }

    @Test
    void connect_inputStreamInitializationFails_closesPortAndThrowsIOException() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.inputStreamFailure = new IOException("boom");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);

        captureAndReportIOExceptionScenario(
                "serial stream initialization failure",
                connection::connect,
                "input/output streams could not be initialized"
        );

        assertFalse(adapter.isOpen());
    }

    @Test
    void sendCommand_success_writesTrimmedCommandWithNewline() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok\n");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        connection.sendCommand("  M105  ");

        assertEquals("M105\n", adapter.getWrittenData());
    }

    @Test
    void sendCommand_blankCommand_throwsIllegalArgumentException() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok\n");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        captureAndReportIllegalArgumentScenario(
                "serial blank command",
                () -> connection.sendCommand("   "),
                "command must not be blank"
        );
    }

    @Test
    void sendCommand_whenNotConnected_throwsIllegalStateException() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);

        captureAndReportIllegalStateScenario(
                "serial send when not connected",
                () -> connection.sendCommand("M105"),
                "Serial connection is not open. Call connect() before sending commands."
        );
    }

    @Test
    void sendCommand_writeFailure_wrapsIOException() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok\n");
        adapter.writeFailure = new IOException("simulated write failure");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        IOException ex = captureAndReportIOExceptionScenario(
                "serial write failure",
                () -> connection.sendCommand("M105"),
                "Failed to send command 'M105' to port 'FAKE_PORT'."
        );

        assertNotNull(ex.getCause());
        assertEquals("simulated write failure", ex.getCause().getMessage());
    }

    @Test
    void readLine_success_returnsTrimmedResponse() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0\n");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        String response = connection.readLine();

        assertEquals("ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0", response);
    }

    @Test
    void readLine_blankInput_timesOut() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("   ");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        captureAndReportTimeoutScenario(
                "serial read timeout blank input",
                connection::readLine,
                "No response received from printer within"
        );
    }

    @Test
    void readLine_whenNotConnected_throwsIllegalStateException() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);

        captureAndReportIllegalStateScenario(
                "serial read when not connected",
                connection::readLine,
                "Serial connection is not open. Call connect() before sending commands."
        );
    }

    @Test
    void readLine_readFailure_wrapsIOException() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.availableFailure = new IOException("simulated read failure");
        adapter.setInputData("");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        IOException ex = captureAndReportIOExceptionScenario(
                "serial read failure",
                connection::readLine,
                "Failed while reading from serial port 'FAKE_PORT'."
        );

        assertNotNull(ex.getCause());
        assertEquals("simulated read failure", ex.getCause().getMessage());
    }

    @Test
    void readLine_partialDataThenQuietPeriod_returnsTrimmedResponse() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok partial");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        String response = connection.readLine();

        assertEquals("ok partial", response);
    }

    @Test
    void disconnect_afterConnect_marksConnectionClosed() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok\n");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        assertTrue(connection.isConnected());

        connection.disconnect();

        assertFalse(connection.isConnected());
        assertFalse(adapter.isOpen());
    }

    @Test
    void disconnect_closePortReturnsFalse_coversErrorPath() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok\n");
        adapter.closeResult = false;

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        stderrHolder[0] = tapSystemErrNormalized(() -> {
            stdoutHolder[0] = tapSystemOutNormalized(connection::disconnect);
        });

        assertFalse(adapter.isOpen());

        TestReportWriter.appendScenario(
                "serial disconnect close port failed",
                1,
                stdoutHolder[0],
                stderrHolder[0]
        );

        assertTrue(stderrHolder[0].contains("Failed to close serial port FAKE_PORT"));
    }

    @Test
    void disconnect_streamCloseFailure_stillClosesPort() throws Exception {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();
        adapter.setInputData("ok\n");
        adapter.closeInputFailure = new IOException("close input failed");
        adapter.closeOutputFailure = new IOException("close output failed");

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);
        connection.connect();

        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        stderrHolder[0] = tapSystemErrNormalized(() -> {
            stdoutHolder[0] = tapSystemOutNormalized(connection::disconnect);
        });

        assertFalse(adapter.isOpen());

        TestReportWriter.appendScenario(
                "serial disconnect stream close failure",
                1,
                stdoutHolder[0],
                stderrHolder[0]
        );

        assertTrue(stderrHolder[0].contains("Failed to close input stream: close input failed"));
        assertTrue(stderrHolder[0].contains("Failed to close output stream: close output failed"));
    }

    @Test
    void isConnected_falseWhenPortNotOpen() {
        TestSerialPortAdapter adapter = new TestSerialPortAdapter();

        SerialConnection connection = new SerialConnection("FAKE_PORT", 115200, adapter);

        assertFalse(connection.isConnected());
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private IOException captureAndReportIOExceptionScenario(String scenarioName,
                                                            CheckedRunnable action,
                                                            String expectedMessagePart) throws Exception {
        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        IOException ex = assertThrows(IOException.class, () -> {
            stderrHolder[0] = tapSystemErrNormalized(() -> {
                stdoutHolder[0] = tapSystemOutNormalized(() -> {
                    action.run();
                });
            });
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart));

        TestReportWriter.appendScenario(
                scenarioName,
                1,
                stdoutHolder[0],
                buildExceptionReport(ex, stderrHolder[0])
        );

        return ex;
    }

    private TimeoutException captureAndReportTimeoutScenario(String scenarioName,
                                                             CheckedRunnable action,
                                                             String expectedMessagePart) throws Exception {
        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        TimeoutException ex = assertThrows(TimeoutException.class, () -> {
            stderrHolder[0] = tapSystemErrNormalized(() -> {
                stdoutHolder[0] = tapSystemOutNormalized(() -> {
                    action.run();
                });
            });
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart));

        TestReportWriter.appendScenario(
                scenarioName,
                4,
                stdoutHolder[0],
                buildExceptionReport(ex, stderrHolder[0])
        );

        return ex;
    }

    private IllegalArgumentException captureAndReportIllegalArgumentScenario(String scenarioName,
                                                                            CheckedRunnable action,
                                                                            String expectedMessagePart) throws Exception {
        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            stderrHolder[0] = tapSystemErrNormalized(() -> {
                stdoutHolder[0] = tapSystemOutNormalized(() -> {
                    action.run();
                });
            });
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart));

        TestReportWriter.appendScenario(
                scenarioName,
                2,
                stdoutHolder[0],
                buildExceptionReport(ex, stderrHolder[0])
        );

        return ex;
    }

    private IllegalStateException captureAndReportIllegalStateScenario(String scenarioName,
                                                                       CheckedRunnable action,
                                                                       String expectedMessagePart) throws Exception {
        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            stderrHolder[0] = tapSystemErrNormalized(() -> {
                stdoutHolder[0] = tapSystemOutNormalized(() -> {
                    action.run();
                });
            });
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart));

        TestReportWriter.appendScenario(
                scenarioName,
                1,
                stdoutHolder[0],
                buildExceptionReport(ex, stderrHolder[0])
        );

        return ex;
    }

    private String buildExceptionReport(Exception ex, String capturedStderr) {
        StringBuilder report = new StringBuilder();
        report.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage());

        if (capturedStderr != null && !capturedStderr.isBlank()) {
            report.append(System.lineSeparator())
                  .append(System.lineSeparator())
                  .append("Captured stderr:")
                  .append(System.lineSeparator())
                  .append(capturedStderr.stripTrailing());
        }

        return report.toString();
    }
}