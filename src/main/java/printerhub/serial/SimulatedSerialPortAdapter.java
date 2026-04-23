package printerhub.serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/*
File: src/main/java/printerhub/serial/SimulatedSerialPortAdapter.java

Purpose:

Runtime simulation adapter for hardware-independent execution.
Provides built-in default responses for common printer commands,
while still allowing system-property overrides for tests or demos.
*/

public class SimulatedSerialPortAdapter implements SerialPortAdapter {

    private final String portName;
    private final ScriptedInputStream inputStream = new ScriptedInputStream();
    private final ScriptedOutputStream outputStream = new ScriptedOutputStream();

    private boolean open;

    public SimulatedSerialPortAdapter(String portName) {
        this.portName = portName;
    }

    @Override
    public void setBaudRate(int baudRate) {
        // no-op in simulated mode
    }

    @Override
    public void setNumDataBits(int numDataBits) {
        // no-op in simulated mode
    }

    @Override
    public void setNumStopBits(int numStopBits) {
        // no-op in simulated mode
    }

    @Override
    public void setParity(int parity) {
        // no-op in simulated mode
    }

    @Override
    public void setComPortTimeouts(int mode, int readTimeout, int writeTimeout) {
        // no-op in simulated mode
    }

    @Override
    public boolean openPort() {
        boolean connect = Boolean.parseBoolean(
                System.getProperty("printerhub.sim.connect", "true")
        );
        open = connect;
        return connect;
    }

    @Override
    public boolean closePort() {
        open = false;
        return true;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public InputStream getInputStream() {
        if (Boolean.parseBoolean(System.getProperty("printerhub.sim.streamInitFail", "false"))) {
            throw new RuntimeException("Simulated input stream initialization failure");
        }
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        if (Boolean.parseBoolean(System.getProperty("printerhub.sim.streamInitFail", "false"))) {
            throw new RuntimeException("Simulated output stream initialization failure");
        }
        return outputStream;
    }

    @Override
    public String getSystemPortName() {
        return portName;
    }

    private void handleCommand(String command) throws IOException {
        if (Boolean.parseBoolean(System.getProperty("printerhub.sim.writeFail", "false"))) {
            throw new IOException("Simulated write failure");
        }

        if (Boolean.parseBoolean(System.getProperty("printerhub.sim.readFail", "false"))) {
            inputStream.setReadFailure(new IOException("Simulated read failure"));
            return;
        }

        String noResponseKey = "printerhub.sim.noresponse." + command;
        if (Boolean.parseBoolean(System.getProperty(noResponseKey, "false"))) {
            inputStream.clear();
            return;
        }

        String responseKey = "printerhub.sim.response." + command;
        String response = System.getProperty(responseKey);

        if (response == null) {
            response = System.getProperty("printerhub.sim.response.default");
        }

        if (response == null) {
            response = defaultResponseFor(command);
        }

        if (response == null || response.isBlank()) {
            inputStream.clear();
            return;
        }

        inputStream.setResponse(ensureLineTerminated(response));
    }

    private String defaultResponseFor(String command) {
        String normalized = command.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "M105" -> "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";
            case "M114" -> "X:0.00 Y:0.00 Z:0.00 E:0.00 Count X:0 Y:0 Z:0";
            case "M115" -> "FIRMWARE_NAME:Marlin SIMULATED SOURCE_CODE_URL:https://marlinfw.org "
                    + "PROTOCOL_VERSION:1.0 MACHINE_TYPE:Ender-3 V2 Neo EXTRUDER_COUNT:1";
            default -> "ok";
        };
    }

    private String ensureLineTerminated(String response) {
        if (response.endsWith("\n")) {
            return response;
        }
        return response + System.lineSeparator();
    }

    private final class ScriptedOutputStream extends OutputStream {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void write(int b) {
            buffer.append((char) b);
        }

        @Override
        public void flush() throws IOException {
            String command = buffer.toString().trim();
            buffer.setLength(0);

            if (!command.isEmpty()) {
                handleCommand(command);
            }
        }
    }

    private static final class ScriptedInputStream extends InputStream {

        private byte[] data = new byte[0];
        private int position = 0;
        private IOException readFailure;

        void setResponse(String response) {
            this.data = response.getBytes(StandardCharsets.UTF_8);
            this.position = 0;
            this.readFailure = null;
        }

        void setReadFailure(IOException readFailure) {
            this.data = new byte[0];
            this.position = 0;
            this.readFailure = readFailure;
        }

        void clear() {
            this.data = new byte[0];
            this.position = 0;
            this.readFailure = null;
        }

        @Override
        public int available() throws IOException {
            if (readFailure != null) {
                throw readFailure;
            }
            return data.length - position;
        }

        @Override
        public int read() throws IOException {
            if (readFailure != null) {
                throw readFailure;
            }
            if (position >= data.length) {
                return -1;
            }
            return data[position++];
        }
    }
}