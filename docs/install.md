# install

## prereauise

### Jenkins CI machine

```bash
sudo apt update
sudo apt install maven
sudo apt install openjdk-21-jdk
```

Checks:

```bash
java -version
mvn -version
```

### Developer machine

```bash
sudo apt update
sudo apt install maven
sudo apt install openjdk-21-jdk
sudo apt install minicom
```



### Only for real hardware access

```bash
sudo usermod -aG dialout $USER
logout
su - $USER
groups
```


## jenkins