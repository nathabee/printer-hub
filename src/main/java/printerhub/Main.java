package printerhub;

public class Main {

    public static void main(String[] args) {

        SerialConnection serial = new SerialConnection();

        try {

            boolean connected =
                    serial.connect("/dev/ttyUSB0");

            if (!connected) return;

            Thread.sleep(2000);

            System.out.println("Sending M105...");

            serial.sendCommand("M105");

            String response =
                    serial.readResponse();

            System.out.println("Response:");
            System.out.println(response);

            serial.disconnect();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}