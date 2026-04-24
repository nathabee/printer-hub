/*
File: src/test/java/printerhub/serial/FakeSerialPortAdapter.java

Purpose:

lets SerialConnection run almost unchanged
simulates low-level serial behavior
*/

package printerhub.serial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class FakeSerialPortAdapter implements SerialPortAdapter {

    public boolean openResult = true;
    public boolean closeResult = true;
    public boolean open = false;
    public String systemPortName;

    private InputStream inputStream = new ByteArrayInputStream(new byte[0]);
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public FakeSerialPortAdapter() {
        this("FAKE_PORT");
    }

    public FakeSerialPortAdapter(String systemPortName) {
        this.systemPortName = systemPortName == null || systemPortName.isBlank()
                ? "FAKE_PORT"
                : systemPortName;
    }

    public void setInputData(String data) {
        String safeData = data == null ? "" : data;
        this.inputStream = new ByteArrayInputStream(safeData.getBytes(StandardCharsets.UTF_8));
    }

    public String getWrittenData() {
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void setBaudRate(int baudRate) {
    }

    @Override
    public void setNumDataBits(int numDataBits) {
    }

    @Override
    public void setNumStopBits(int numStopBits) {
    }

    @Override
    public void setParity(int parity) {
    }

    @Override
    public void setComPortTimeouts(int mode, int readTimeout, int writeTimeout) {
    }

    @Override
    public boolean openPort() {
        open = openResult;
        return openResult;
    }

    @Override
    public boolean closePort() {
        boolean result = closeResult;
        open = false;
        return result;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public String getSystemPortName() {
        return systemPortName;
    }
}