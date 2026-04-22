package printerhub;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class FakePrinterPort implements PrinterPort {
    public boolean connectResult = true;
    public boolean disconnectCalled = false;
    public IOException connectException;
    public IOException sendException;
    public IOException readException;

    private final String portName;
    private final Deque<String> responses = new ArrayDeque<>();
    private final List<String> sentCommands = new ArrayList<>();

    public FakePrinterPort(String portName) {
        this.portName = portName;
    }

    public void queueResponse(String response) {
        responses.addLast(response);
    }

    public List<String> getSentCommands() {
        return sentCommands;
    }

    @Override
    public boolean connect() throws IOException {
        if (connectException != null) {
            throw connectException;
        }
        return connectResult;
    }

    @Override
    public void disconnect() {
        disconnectCalled = true;
    }

    @Override
    public void sendCommand(String command) throws IOException {
        if (sendException != null) {
            throw sendException;
        }
        sentCommands.add(command);
    }
 
    public String readLine() throws IOException, TimeoutException, InterruptedException {
        if (readException != null) {
            throw readException;
        }
        return responses.isEmpty() ? null : responses.removeFirst();
    }

    @Override
    public String getPortName() {
        return portName;
    }
}