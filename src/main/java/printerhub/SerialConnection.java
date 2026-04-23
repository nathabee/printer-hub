package printerhub;

import printerhub.serial.SerialPortAdapter;
import printerhub.serial.SerialPortAdapterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;

public class SerialConnection implements PrinterPort {

    private static final int READ_TIMEOUT_MS = 2000;
    private static final int QUIET_PERIOD_MS = 200;
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String portName;
    private final int baudRate;
    private final SerialPortAdapter port;

    private InputStream in;
    private OutputStream out;

    public SerialConnection(String portName, int baudRate) {
        validatePortName(portName);
        this.portName = portName;
        this.baudRate = baudRate;
        this.port = SerialPortAdapterFactory.createReal(portName);
    }

    public SerialConnection(String portName, int baudRate, SerialPortAdapter portAdapter) {
        validatePortName(portName);
        if (portAdapter == null) {
            throw new IllegalArgumentException(OperationMessages.PORT_ADAPTER_MUST_NOT_BE_NULL);
        }

        this.portName = portName;
        this.baudRate = baudRate;
        this.port = portAdapter;
    }

    @Override
    public boolean connect() throws IOException {
        if (isConnected()) {
            throw new IllegalStateException(
                    OperationMessages.connectionAlreadyOpen(port.getSystemPortName())
            );
        }

        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPortAdapter.ONE_STOP_BIT);
        port.setParity(SerialPortAdapter.NO_PARITY);
        port.setComPortTimeouts(SerialPortAdapter.TIMEOUT_NONBLOCKING, 0, 0);

        if (!port.openPort()) {
            throw new IOException(OperationMessages.failedToOpenSerialPort(portName));
        }

        try {
            in = port.getInputStream();
            out = port.getOutputStream();
        } catch (Exception e) {
            safelyClosePortOnly();
            throw new IOException(
                    OperationMessages.streamInitializationFailed(portName),
                    e
            );
        }

        logInfo("Connected to " + portName);
        return true;
    }

    @Override
    public void sendCommand(String command) throws IOException {
        ensureConnected();

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.COMMAND_MUST_NOT_BE_BLANK);
        }

        String trimmedCommand = command.trim();
        String fullCommand = trimmedCommand + "\n";

        try {
            out.write(fullCommand.getBytes(StandardCharsets.UTF_8));
            out.flush();
            logSent(trimmedCommand);
        } catch (IOException e) {
            throw new IOException(
                    OperationMessages.failedToSendCommand(trimmedCommand, port.getSystemPortName()),
                    e
            );
        }
    }

    @Override
    public String readLine() throws IOException, TimeoutException, InterruptedException {
        ensureConnected();

        StringBuilder response = new StringBuilder();
        long start = System.currentTimeMillis();
        long lastDataTime = start;

        while (System.currentTimeMillis() - start < READ_TIMEOUT_MS) {
            boolean readSomething = false;

            try {
                while (in.available() > 0) {
                    int value = in.read();
                    if (value >= 0) {
                        response.append((char) value);
                        readSomething = true;
                        lastDataTime = System.currentTimeMillis();
                    }
                }
            } catch (IOException e) {
                throw new IOException(
                        OperationMessages.failedWhileReading(port.getSystemPortName()),
                        e
                );
            }

            if (readSomething) {
                Thread.sleep(50);
            } else {
                if (response.length() > 0
                        && System.currentTimeMillis() - lastDataTime > QUIET_PERIOD_MS) {
                    break;
                }
                Thread.sleep(25);
            }
        }

        String cleaned = response.toString().trim();

        if (cleaned.isEmpty()) {
            throw new TimeoutException(
                    OperationMessages.noResponseWithinTimeout(READ_TIMEOUT_MS)
            );
        }

        logReceived(cleaned);
        return cleaned;
    }

    @Override
    public void disconnect() {
        closeQuietly(in, "input stream");
        in = null;

        closeQuietly(out, "output stream");
        out = null;

        try {
            String currentPortName = port.getSystemPortName();
            boolean wasOpen = port.isOpen();

            if (wasOpen && !port.closePort()) {
                logError(OperationMessages.failedToCloseSerialPort(currentPortName));
            } else if (wasOpen) {
                logInfo(OperationMessages.DISCONNECTED);
            }
        } catch (Exception e) {
            logError(OperationMessages.errorWhileClosingSerialPort(e.getMessage()));
        }
    }

    @Override
    public String getPortName() {
        return portName;
    }

    public boolean isConnected() {
        return port.isOpen() && in != null && out != null;
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new IllegalStateException(OperationMessages.SERIAL_NOT_OPEN);
        }
    }

    private void safelyClosePortOnly() {
        try {
            if (port.isOpen()) {
                port.closePort();
            }
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(AutoCloseable closeable, String resourceName) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                logError(OperationMessages.failedToCloseResource(resourceName, e.getMessage()));
            }
        }
    }

    private void logSent(String message) {
        System.out.println(timestamp() + " " + OperationMessages.sentMessage(message));
    }

    private void logReceived(String message) {
        String normalized = message.replace("\r", "").replace("\n", " | ");
        System.out.println(timestamp() + " " + OperationMessages.receivedMessage(normalized));
    }

    private void logInfo(String message) {
        System.out.println(timestamp() + " " + OperationMessages.infoMessage(message));
    }

    private void logError(String message) {
        System.err.println(timestamp() + " " + OperationMessages.errorMessage(message));
    }

    private String timestamp() {
        return LocalDateTime.now().format(TS_FORMAT);
    }

    private static void validatePortName(String portName) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PORT_NAME_MUST_NOT_BE_BLANK);
        }
    }
}