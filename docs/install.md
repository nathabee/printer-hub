# install

## prereauise


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


 
## Jenkins configuration

### Install required tools on Jenkins machine

```bash
sudo apt update
sudo apt install openjdk-21-jdk
sudo apt install maven
```

Check:

```bash
java -version
mvn -version
```

---

### Add GitHub credential (recommended)

Jenkins → Manage Jenkins → Credentials → Add

Type:

```text
Secret text
```

ID:

```text
github-token
```

Value:

```text
your GitHub personal access token
```

Description:

```text
GitHub access token
```

---

### Create Jenkins pipeline job

Jenkins → New Item

Name:

```text
printer-hub-ci
```

Type:

```text
Pipeline
```

Click **OK**

---

### Configure pipeline source

In job configuration:

Pipeline:

```text
Pipeline script from SCM
```

SCM:

```text
Git
```

Repository URL:

```text
https://github.com/<your-user>/printer-hub.git
```

Credentials:

```text
github-token   (if configured)
```

Branch:

```text
*/main
```

Script Path:

```text
Jenkinsfile
```

Save.

---

### Run first build

Open job:

```text
printer-hub-ci
```

Click:

```text
Build Now
```

Expected result:

* Maven build runs
* Tests executed
* JaCoCo report generated
* Build marked SUCCESS

 