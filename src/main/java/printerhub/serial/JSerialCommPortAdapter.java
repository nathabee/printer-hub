package printerhub.serial;

import com.fazecast.jSerialComm.SerialPort;

import java.io.InputStream;
import java.io.OutputStream;

public final class JSerialCommPortAdapter implements SerialPortAdapter {

    private final SerialPort delegate;

    public JSerialCommPortAdapter(String portName) {
        this.delegate = SerialPort.getCommPort(portName);
    }

    @Override
    public void setBaudRate(int baudRate) {
        delegate.setBaudRate(baudRate);
    }

    @Override
    public void setNumDataBits(int numDataBits) {
        delegate.setNumDataBits(numDataBits);
    }

    @Override
    public void setNumStopBits(int numStopBits) {
        delegate.setNumStopBits(numStopBits);
    }

    @Override
    public void setParity(int parity) {
        delegate.setParity(parity);
    }

    @Override
    public void setComPortTimeouts(int mode, int readTimeout, int writeTimeout) {
        delegate.setComPortTimeouts(mode, readTimeout, writeTimeout);
    }

    @Override
    public boolean openPort() {
        return delegate.openPort();
    }

    @Override
    public boolean closePort() {
        return delegate.closePort();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public InputStream getInputStream() {
        return delegate.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        return delegate.getOutputStream();
    }

    @Override
    public String getSystemPortName() {
        return delegate.getSystemPortName();
    }
}