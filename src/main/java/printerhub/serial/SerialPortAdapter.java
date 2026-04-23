/* 
src/main/java/printerhub/serial/SerialPortAdapter.java

Purpose:

abstraction for the low-level serial object
replaces direct dependency on jSerialComm in SerialConnection
*/

package printerhub.serial;

import java.io.InputStream;
import java.io.OutputStream;



public interface SerialPortAdapter {
    int ONE_STOP_BIT = 1;
    int NO_PARITY = 0;
    int TIMEOUT_NONBLOCKING = 0;

    void setBaudRate(int baudRate);

    void setNumDataBits(int numDataBits);

    void setNumStopBits(int numStopBits);

    void setParity(int parity);

    void setComPortTimeouts(int mode, int readTimeout, int writeTimeout);

    boolean openPort();

    boolean closePort();

    boolean isOpen();

    InputStream getInputStream();

    OutputStream getOutputStream();

    String getSystemPortName();
}