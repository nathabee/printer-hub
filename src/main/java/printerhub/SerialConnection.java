package printerhub;

import printerhub.config.SerialDefaults;
import printerhub.serial.JSerialCommPortAdapter;
import printerhub.serial.SerialPortAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public final class SerialConnection implements PrinterPort {

    private final String portName;
    private final int baudRate;
    private final SerialPortAdapter port;

    private InputStream in;
    private OutputStream out;

    public SerialConnection(String portName) {
        this(portName, SerialDefaults.DEFAULT_BAUD_RATE);
    }

    public SerialConnection(String portName, int baudRate) {
        this(portName, baudRate, new JSerialCommPortAdapter(portName));
    }

    public SerialConnection(String portName, int baudRate, SerialPortAdapter portAdapter) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PORT_NAME_MUST_NOT_BE_BLANK);
        }
        if (portAdapter == null) {
            throw new IllegalArgumentException(OperationMessages.PORT_ADAPTER_MUST_NOT_BE_NULL);
        }

        this.portName = portName.trim();
        this.baudRate = baudRate;
        this.port = portAdapter;
    }

    @Override
    public synchronized void connect() {
        if (isConnected()) {
            return;
        }

        port.setBaudRate(baudRate);
        port.setNumDataBits(SerialPortAdapter.EIGHT_DATA_BITS);
        port.setNumStopBits(SerialPortAdapter.ONE_STOP_BIT);
        port.setParity(SerialPortAdapter.NO_PARITY);
        port.setComPortTimeouts(SerialPortAdapter.TIMEOUT_NONBLOCKING, 0, 0);

        if (!port.openPort()) {
            throw new IllegalStateException(OperationMessages.failedToOpenSerialPort(portName));
        }

        try {
            in = port.getInputStream();
            out = port.getOutputStream();
        } catch (Exception exception) {
            safelyClosePortOnly();
            throw new IllegalStateException(
                    OperationMessages.failedToInitializeSerialStreams(portName),
                    exception
            );
        }
    }

    @Override
    public synchronized String sendCommand(String command) {
        ensureConnected();

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.COMMAND_MUST_NOT_BE_BLANK);
        }

        String trimmedCommand = command.trim();

        try {
            out.write((trimmedCommand + SerialDefaults.DEFAULT_COMMAND_TERMINATOR)
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readLine();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    OperationMessages.failedToSendCommand(trimmedCommand, portName),
                    exception
            );
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    OperationMessages.noResponseForCommandOnPort(trimmedCommand, portName),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    OperationMessages.interruptedWhileReadingResponse(portName),
                    exception
            );
        }
    }

    @Override
    public synchronized void disconnect() {
        closeQuietly(in, "serial input stream");
        in = null;

        closeQuietly(out, "serial output stream");
        out = null;

        try {
            if (port.isOpen()) {
                port.closePort();
            }
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToCloseSerialPort(
                    portName,
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR
                    )
            ));
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

        while (System.currentTimeMillis() - start < SerialDefaults.READ_TIMEOUT_MS) {
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
                Thread.sleep(SerialDefaults.READ_ACTIVITY_SLEEP_MS);
            } else {
                if (response.length() > 0
                        && System.currentTimeMillis() - lastDataTime > SerialDefaults.QUIET_PERIOD_MS) {
                    break;
                }
                Thread.sleep(SerialDefaults.READ_IDLE_SLEEP_MS);
            }
        }

        String cleaned = response.toString().trim();

        if (cleaned.isEmpty()) {
            throw new TimeoutException(
                    OperationMessages.noResponseWithinTimeout(SerialDefaults.READ_TIMEOUT_MS)
            );
        }

        return cleaned;
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new IllegalStateException(
                    OperationMessages.SERIAL_CONNECTION_IS_NOT_OPEN + " " + portName
            );
        }
    }

    private void safelyClosePortOnly() {
        try {
            if (port.isOpen()) {
                port.closePort();
            }
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToCloseSerialPort(
                    portName,
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR
                    )
            ));
        }
    }

    private void closeQuietly(AutoCloseable closeable, String resourceName) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToCloseResource(
                    resourceName,
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR
                    )
            ));
        }
    }
}