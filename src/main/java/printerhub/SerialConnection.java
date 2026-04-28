package printerhub;

import printerhub.serial.SerialPortAdapter;
import printerhub.serial.JSerialCommPortAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public final class SerialConnection implements PrinterPort {

    private static final int DEFAULT_BAUD_RATE = 115200;
    private static final int READ_TIMEOUT_MS = 2000;
    private static final int QUIET_PERIOD_MS = 200;

    private final String portName;
    private final int baudRate;
    private final SerialPortAdapter port;

    private InputStream in;
    private OutputStream out;

    public SerialConnection(String portName) {
        this(portName, DEFAULT_BAUD_RATE);
    }

    public SerialConnection(String portName, int baudRate) {
        this(portName, baudRate, new JSerialCommPortAdapter(portName));
    }

    public SerialConnection(String portName, int baudRate, SerialPortAdapter portAdapter) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException("portName must not be blank");
        }
        if (portAdapter == null) {
            throw new IllegalArgumentException("portAdapter must not be null");
        }

        this.portName = portName;
        this.baudRate = baudRate;
        this.port = portAdapter;
    }

    @Override
    public synchronized void connect() {
        if (isConnected()) {
            return;
        }

        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPortAdapter.ONE_STOP_BIT);
        port.setParity(SerialPortAdapter.NO_PARITY);
        port.setComPortTimeouts(SerialPortAdapter.TIMEOUT_NONBLOCKING, 0, 0);

        if (!port.openPort()) {
            throw new IllegalStateException("Failed to open serial port: " + portName);
        }

        try {
            in = port.getInputStream();
            out = port.getOutputStream();
        } catch (Exception exception) {
            safelyClosePortOnly();
            throw new IllegalStateException("Failed to initialize serial streams for port: " + portName, exception);
        }
    }

    @Override
    public synchronized String sendCommand(String command) {
        ensureConnected();

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }

        String trimmedCommand = command.trim();

        try {
            out.write((trimmedCommand + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readLine();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send command '" + trimmedCommand + "' to " + portName, exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("No response for command '" + trimmedCommand + "' on " + portName, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading response from " + portName, exception);
        }
    }

    @Override
    public synchronized void disconnect() {
        closeQuietly(in);
        in = null;

        closeQuietly(out);
        out = null;

        try {
            if (port.isOpen()) {
                port.closePort();
            }
        } catch (Exception ignored) {
            // Shutdown must continue even if the serial port close operation fails.
        }
    }

    public String portName() {
        return portName;
    }

    public boolean isConnected() {
        return port.isOpen() && in != null && out != null;
    }

    private String readLine() throws IOException, TimeoutException, InterruptedException {
        StringBuilder response = new StringBuilder();
        long start = System.currentTimeMillis();
        long lastDataTime = start;

        while (System.currentTimeMillis() - start < READ_TIMEOUT_MS) {
            boolean readSomething = false;

            while (in.available() > 0) {
                int value = in.read();
                if (value >= 0) {
                    response.append((char) value);
                    readSomething = true;
                    lastDataTime = System.currentTimeMillis();
                }
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
            throw new TimeoutException("No response within " + READ_TIMEOUT_MS + " ms");
        }

        return cleaned;
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new IllegalStateException("Serial port is not open: " + portName);
        }
    }

    private void safelyClosePortOnly() {
        try {
            if (port.isOpen()) {
                port.closePort();
            }
        } catch (Exception ignored) {
            // Nothing else can be done here.
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception ignored) {
            // Shutdown must continue even if a stream close operation fails.
        }
    }
}