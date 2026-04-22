package printerhub;

import com.fazecast.jSerialComm.SerialPort;

import java.io.InputStream;
import java.io.OutputStream;

public class SerialConnection {

    private SerialPort port;
    private InputStream in;
    private OutputStream out;

    public boolean connect(String portName) {

        port = SerialPort.getCommPort(portName);

        port.setBaudRate(115200);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);

        if (!port.openPort()) {
            System.out.println("Failed to open port.");
            return false;
        }

        in = port.getInputStream();
        out = port.getOutputStream();

        System.out.println("Connected to " + portName);

        return true;
    }

    public void sendCommand(String command) throws Exception {

        String cmd = command + "\n";

        out.write(cmd.getBytes());
        out.flush();
    }

    public String readResponse() throws Exception {

        StringBuilder response = new StringBuilder();

        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < 2000) {

            while (in.available() > 0) {

                char c = (char) in.read();
                response.append(c);
            }
        }

        return response.toString();
    }

    public void disconnect() {

        if (port != null) {

            port.closePort();
            System.out.println("Disconnected.");
        }
    }
}