package printerhub;

public interface PrinterPort {

    void connect();

    String sendCommand(String command);

    void disconnect();
}