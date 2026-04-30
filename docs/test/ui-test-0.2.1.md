## Test steps



 

```text
mvn exec:java   -Dprinterhub.databaseFile="printerhub-real.db"   -Dprinterhub.api.port=18080   -Dexec.mainClass="printerhub.Main"

# open in browser :
http://localhost:18080/dashboard
```

Then check these UI cases.

1. Basic runtime

* `/health` returns ok
* dashboard loads
* printer list loads
* monitoring rules load
* no JS error in browser console

2. Printer administration

* create one `sim` printer
* create one `sim-error` printer
* create one `sim-timeout` printer
* disable and enable each one
* edit name, mode, and port
* delete one printer
* verify configured printers stay visible in admin list
* verify disabled printers are still visible for administration

3. Monitoring behavior
   For `sim`:

* state should become `IDLE`
* temperatures should appear
* last response should appear

For `sim-error`:

* state should become `ERROR`
* command failure-like response should appear

For `sim-timeout`:

* state should become `ERROR`
* timeout message should appear

For disabled printer:

* state should show disabled/disconnected style, not operational failure style

4. Monitoring rules
   These rules are **global runtime settings**, not per-printer.
   So:

* change poll interval
* change snapshot minimum interval
* change temperature delta threshold
* change dedup window
* change error persistence behavior
* save
* reload page
* verify values are still there

Expected behavior:

* changing rules affects the whole runtime
* it does not overwrite one printer differently from another
* printers should continue updating after rule changes
* if poll interval changes, updates should become visibly faster or slower

5. Manual command execution
   On a `sim` printer, test from UI if present, or by endpoint if UI is partial:

* `M105`
* `M114`
* `M115`
* `M104` with temperature
* `M140` with temperature
* `M106`
* `M107`

Check:

* successful commands return a response
* invalid command is rejected
* missing temperature for `M104` or `M140` is rejected
* command-related events appear in printer events

6. Events
   Check that `GET /printers/{id}/events` returns:

* monitoring events
* manual command success events
* manual command failure events

7. Real printer sanity check
   For the real printer:

* verify the current device path first with `dmesg` or `/dev/serial/by-id`
* create the printer with the actual current port
* check whether `M105` works through manual command execution
* if it fails, compare with direct manual serial access before blaming the runtime

What you should expect about settings:

* in `0.2.1`, monitoring settings are **runtime-global**
* per-printer tuning is not the expected behavior here
 