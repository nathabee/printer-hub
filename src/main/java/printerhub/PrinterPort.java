package printerhub;

public interface PrinterPort {

    void connect();

    String sendCommand(String command);

    String sendRawLine(String line);

    void disconnect();
}