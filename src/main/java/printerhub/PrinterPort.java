package printerhub;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public interface PrinterPort {
    boolean connect() throws IOException;

    void disconnect();

    void sendCommand(String command) throws IOException;

    String readLine() throws IOException, TimeoutException, InterruptedException;

    String getPortName();
}