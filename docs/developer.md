


## java project 

### structure



### test

```bash
# first installation
sudo apt install maven
# check version
mvn --version
java --version

# clean and compile
mvn clean compile

# compile
mvn compile


# make sure minicom is closed
# verify the port is free:
sudo lsof /dev/ttyUSB0


# exec
mvn exec:java -Dexec.mainClass="printerhub.Main"
```

