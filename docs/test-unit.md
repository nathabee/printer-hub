Unit Tests



## monitoring and runtime

Mostly **unit-style tests with small fakes/stubs**.

Examples:

* `PrinterRegistryTest`: uses a tiny fake `PrinterPort`
* `PrinterMonitoringTaskTest`: uses a fake `PrinterPort`, but **real SQLite stores**
* `PrinterMonitoringSchedulerTest`: mostly lifecycle/state tests, not deep thread timing tests
* `SimulatedPrinterPortTest`: pure unit test, no DB, no HTTP

So Step B is a mix:

* pure unit tests
* unit tests with light fake collaborators
* a few “near-integration” tests where real SQLite is used because the task persists snapshots/events

## persistence : database

This is **persistence integration testing**.

Here the database is absolutely tested.

We are using:

* real `sqlite-jdbc`
* real `DatabaseInitializer`
* real store classes
* temp DB files per test

And yes, database content is verified directly.

We are **not** using a mocking library for DB assertions.
We check DB state with real SQL queries, typically:

```text id="ye12l5"
SELECT COUNT(*)
SELECT specific columns from printer_events / printer_snapshots / configured_printers
```

So Step C is not “fake DB” testing. It is real SQLite testing.

## API

Yes, the **HTTP layer is really tested**.

`RemoteApiServerTest` starts the real embedded HTTP server on a temporary local port and sends real HTTP requests using Java’s HTTP client.

So in Step D we test:

* request routing
* HTTP methods
* response status codes
* response bodies
* dashboard static resources

That means:

* `GET /health`
* `GET /printers`
* `POST /printers`
* `PUT /printers/{id}`
* etc.

are actual HTTP calls, not mocked controller calls.

## Dashboard + API + DB

Usually only **indirectly**.

Step D mainly verifies API behavior through HTTP responses and runtime state changes.

Some DB impact happens because the real API uses the real persistence layer, but Step D is **not primarily a DB-content verification step**.

So:

```text id="a31gx4"
Step C = direct DB verification
Step D = direct HTTP verification
```

## Are we using a mocking library?

No, not really.

You currently have:

* JUnit 5
* system-lambda
* sqlite-jdbc

No Mockito in the pom.

So the testing style is:

* hand-written stubs/fakes
* real SQLite for persistence checks
* real local HTTP server for API checks
* direct SQL queries for DB assertions
 