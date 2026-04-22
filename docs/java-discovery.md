# Java Discovery Notes — PrinterHub

Short technical notes capturing the minimum Java knowledge required to understand the project.

Not a tutorial — just operational understanding.

---

## Maven Execution Model

Command used:

```bash
mvn exec:java -Dexec.mainClass="printerhub.Main"
````

Meaning:

* Maven compiles sources from:

```text
src/main/java
```

* `printerhub.Main` is the **fully qualified class name**

Mapping:

```text
printerhub.Main
= package printerhub
= class Main
= file src/main/java/printerhub/Main.java
```

Important:

Java requires the file path to match the package name.

---

## Basic Java Program Structure

Minimal Java program:

```java
package printerhub;

public class Main {

    public static void main(String[] args) {

        System.out.println("Hello PrinterHub");

    }
}
```

Key elements:

* `package` → logical namespace
* `class` → container for code
* `main()` → program entry point
* `System.out.println()` → console output

Execution starts in:

```text
main()
```

Always.

---

## Classes — Core Java Unit

A class defines behavior.

Example:

```java
public class SerialConnection {

    public boolean connect(String portName) {

        return true;

    }
}
```

Meaning:

* `class` = blueprint
* functions live inside classes

---

## Methods (Functions)

Method structure:

```java
public boolean connect(String portName)
```

Parts:

```text
public      → visibility
boolean     → return type
connect     → method name
(String...) → parameters
```

Return types:

```text
void     → returns nothing
boolean  → true/false
String   → text
int      → number
```

---

## Object Creation

Using a class:

```java
SerialConnection serial =
        new SerialConnection();
```

Meaning:

```text
Create an object from class
```

Pattern:

```text
ClassName variable = new ClassName();
```

---

## Calling Methods

Example:

```java
serial.connect("/dev/ttyUSB0");
```

Meaning:

```text
object.method(parameters)
```

Very common pattern.

---

## Exception Handling

Used to handle errors.

Example:

```java
try {

    serial.sendCommand("M105");

} catch (Exception e) {

    e.printStackTrace();

}
```

Meaning:

```text
Try operation
If error occurs → catch it
```

Important for:

```text
hardware communication
file IO
networking
```

---

## Thread.sleep()

Used to pause execution.

Example:

```java
Thread.sleep(2000);
```

Meaning:

```text
Wait 2000 milliseconds (2 seconds)
```

Used here to:

```text
Allow printer to initialize
```

---

## String Handling

Used to send commands.

Example:

```java
String cmd = command + "\n";
```

Meaning:

```text
Append newline to command
```

Required for:

```text
G-code execution
```

Printer expects:

```text
command + newline
```

---

## Input / Output Streams

Used for communication.

Example:

```java
InputStream in;
OutputStream out;
```

Meaning:

```text
in  → read data
out → send data
```

Typical data flow:

```text
Java → OutputStream → Printer
Printer → InputStream → Java
```

---

## Serial Communication Model

Current data flow:

```text
Java Program
      |
Serial Library (jSerialComm)
      |
/dev/ttyUSB0
      |
Printer Firmware (Marlin)
```

Command flow:

```text
Send M105
Receive temperature
```

Example response:

```text
ok T:20.51 /0.00 B:20.43 /0.00
```

Meaning:

```text
Hotend temperature
Bed temperature
```

---

## Maven Project Layout

Standard structure:

```text
printer-hub/

src/
 └── main/
     └── java/
         └── printerhub/
             ├── Main.java
             └── SerialConnection.java

docs/
 └── java-discovery.md

pom.xml
```

Important:

```text
Maven expects this structure.
```

Changing it requires configuration.

---

## Libraries (Dependencies)

Used:

```text
jSerialComm
```

Defined in:

```xml
pom.xml
```

Purpose:

```text
Handle serial communication
```

Without it:

```text
USB communication is difficult.
```

---

## What We Have Learned So Far

Core Java concepts now used:

```text
package structure
classes
methods
objects
method calls
exception handling
input/output streams
string handling
thread timing
external libraries
maven build system
```

That is already the **core of applied Java**.

Not theoretical — operational.

---

## Current Capability Achieved

Working system:

```text
Java → Printer → Temperature response
```

Confirmed output:

```text
ok T:20.51 /0.00 B:20.43 /0.00
```

Meaning:

```text
Stage 2 working correctly.
```

---

## Next Concepts to Learn (Stage 2 continuation)

Focus topics:

```text
Loop execution
Logging output
Reading continuous data
Parsing responses
Device state modeling
```

Not advanced Java yet — practical Java.

```