 

### 0.0.2 and 0.0.3

Stage 2 includes:

serial connection to printer
command send/receive logging
repeated status polling
basic printer initialization wait
clean disconnect
operational test against real hardware
basic handling of earlier port-access issues

 


### 0.0.4  

**Automated testing foundation**

Deliverables:

* extract serial communication behind an interface
* move polling logic into a testable service
* add JUnit tests
* add fake serial implementation
* test success case
* test no-response case
* test disconnect/error case
* test repeated polling behavior


### 0.0.5

**JaCoCo coverage**


### 0.0.6

**Jenkins CI**

* checkout repo
* run `mvn test`
* maybe `mvn verify`
* archive test reports

And only after that:

### 0.0.7

**Optional Dockerized CI runner**

* Dockerfile for Maven/Java build
* maybe Jenkins pipeline using container agent
* no real printer required in container
 