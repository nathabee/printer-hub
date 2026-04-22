package printerhub;

import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;

public class SerialConnection {

    private static final int BAUD_RATE = 115200;
    private static final int READ_TIMEOUT_MS = 2000;
    private static final int QUIET_PERIOD_MS = 200;
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SerialPort port;
    private InputStream in;
    private OutputStream out;

    public void connect(String portName) throws IOException {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException("portName must not be blank");
        }

        if (isConnected()) {
            throw new IllegalStateException(
                    "Connection already open on port " + port.getSystemPortName()
            );
        }

        port = SerialPort.getCommPort(portName);

        port.setBaudRate(BAUD_RATE);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (!port.openPort()) {
            port = null;
            throw new IOException(
                    "Failed to open serial port '" + portName + "'. " +
                    "Possible causes: device path is wrong, permission is missing, " +
                    "or the port is already in use."
            );
        }

        try {
            in = port.getInputStream();
            out = port.getOutputStream();
        } catch (Exception e) {
            safelyClosePortOnly();
            throw new IOException(
                    "Serial port '" + portName + "' opened, but input/output streams could not be initialized.",
                    e
            );
        }

        logInfo("Connected to " + portName);
    }

    public void sendCommand(String command) throws IOException {
        ensureConnected();

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }

        String fullCommand = command.trim() + "\n";

        try {
            out.write(fullCommand.getBytes(StandardCharsets.UTF_8));
            out.flush();
            logSent(command.trim());
        } catch (IOException e) {
            throw new IOException(
                    "Failed to send command '" + command.trim() + "' to port '" +
                    port.getSystemPortName() + "'.",
                    e
            );
        }
    }

    public String readResponse() throws IOException, TimeoutException, InterruptedException {
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
                        "Failed while reading from serial port '" +
                        port.getSystemPortName() + "'.",
                        e
                );
            }

            if (readSomething) {
                Thread.sleep(50);
            } else {
                if (response.length() > 0 &&
                        System.currentTimeMillis() - lastDataTime > QUIET_PERIOD_MS) {
                    break;
                }
                Thread.sleep(25);
            }
        }

        String cleaned = response.toString().trim();

        if (cleaned.isEmpty()) {
            throw new TimeoutException(
                    "No response received from printer within " + READ_TIMEOUT_MS + " ms."
            );
        }

        logReceived(cleaned);
        return cleaned;
    }

    public void disconnect() {
        closeQuietly(in, "input stream");
        in = null;

        closeQuietly(out, "output stream");
        out = null;

        if (port != null) {
            try {
                if (port.isOpen() && !port.closePort()) {
                    logError("Failed to close serial port " + port.getSystemPortName());
                } else if (port.isOpen()) {
                    logInfo("Disconnected.");
                } else {
                    logInfo("Disconnected.");
                }
            } catch (Exception e) {
                logError("Error while closing serial port: " + e.getMessage());
            } finally {
                port = null;
            }
        }
    }

    public boolean isConnected() {
        return port != null && port.isOpen() && in != null && out != null;
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new IllegalStateException(
                    "Serial connection is not open. Call connect() before sending commands."
            );
        }
    }

    private void safelyClosePortOnly() {
        if (port != null) {
            try {
                if (port.isOpen()) {
                    port.closePort();
                }
            } catch (Exception ignored) {
            } finally {
                port = null;
            }
        }
    }

    private void closeQuietly(AutoCloseable closeable, String resourceName) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                logError("Failed to close " + resourceName + ": " + e.getMessage());
            }
        }
    }

    private void logSent(String message) {
        System.out.println(timestamp() + " [SEND] " + message);
    }

    private void logReceived(String message) {
        String normalized = message.replace("\r", "").replace("\n", " | ");
        System.out.println(timestamp() + " [RECV] " + normalized);
    }

    private void logInfo(String message) {
        System.out.println(timestamp() + " [INFO] " + message);
    }

    private void logError(String message) {
        System.err.println(timestamp() + " [ERROR] " + message);
    }

    private String timestamp() {
        return LocalDateTime.now().format(TS_FORMAT);
    }
}