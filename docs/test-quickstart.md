#  Test Quickstart


what do do after change for snhell local test?

##  Local farm:

```bash
mvn -DskipTests compile

mvn \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.databaseFile=spaghettichef-local.db \
  -Dspaghettichef.api.port=18080 \
  exec:java
```

Open:

```text
http://localhost:18080/dashboard
```

## Central viewer in another terminal:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.central.CentralMain" \
  -Dspaghettichef.central.databaseFile=spaghettichef-central.db \
  -Dspaghettichef.api.port=18180 \
  exec:java
```
or Alternative command, also valid:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.mode=central \
  -Dspaghettichef.central.databaseFile=spaghettichef-central.db \
  -Dspaghettichef.api.port=18180 \
  exec:java

```


Open:

```text
http://localhost:18180/central-dashboard
```

Alternative central command through `spaghettichef.Main`:

```bash
mvn \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.mode=central \
  -Dspaghettichef.central.databaseFile=spaghettichef-central.db \
  -Dspaghettichef.api.port=18180 \
  exec:java
```
