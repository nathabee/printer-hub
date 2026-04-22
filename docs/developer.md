# Developper Guide


##   File structure and purpose


### src/main/java/printerhub/Main.java

CLI entry point.
Parses arguments, validates input, creates the real serial connection, starts polling, and maps failures to exit codes.

### src/main/java/printerhub/PrinterPoller.java

Core polling workflow.
Connects to the printer port, waits for initialization, sends the command repeatedly, reads responses, and disconnects safely.

### src/main/java/printerhub/PrinterPort.java

Abstraction interface for printer communication.
Lets the app use either the real serial connection or a fake test implementation.

### src/main/java/printerhub/SerialConnection.java

Real hardware implementation.
Uses jSerialComm to open /dev/ttyUSB0, send commands, read printer responses, and handle serial I/O errors.

### src/test/java/printerhub/FakePrinterPort.java

Fake printer for tests.
Simulates connect, send, read, timeout, and failure cases without needing a real printer.

### src/test/java/printerhub/PrinterPollerTest.java

Automated unit tests for polling logic.
Verifies normal polling and the main error scenarios.



## compile and run


### install 

- on dev
```bash
# first installation
sudo apt install maven
# check version
mvn --version
java --version


# make sure minicom is closed
# verify the port is free:
sudo lsof /dev/ttyUSB0
```

### compile and run all
  
```
mvn clean compile
mvn exec:java

```


###  clean and compile and exec


```
mvn clean compile 
mvn exec:java -Dexec.mainClass="printerhub.Main"

```

### compile and run test


```
mvn clean compile
mvn test 

```



### coverage test


```
mvn clean verify

# check outpu on Ubuntu :

xdg-open target/site/jacoco/index.html


```


### before a commit in github


```
 
mvn clean verify

# check outpu on Ubuntu :

xdg-open target/site/jacoco/index.html


# If you changed hardware/runtime behavior too
 
mvn exec:java -Dexec.mainClass="printerhub.Main"




```

