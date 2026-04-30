
# 0.2.1 UI test

## Dashboard load
- [ ] dashboard opens
- [ ] printer list loads
- [ ] monitoring rules load
- [ ] no browser console error

## Printer CRUD
- [ ] create sim printer
- [ ] create sim-error printer
- [ ] create sim-timeout printer
- [ ] edit printer
- [ ] enable printer
- [ ] disable printer
- [ ] delete printer

## Monitoring states
- [ ] sim -> IDLE
- [ ] sim -> temperatures shown
- [ ] sim-error -> ERROR
- [ ] sim-timeout -> ERROR
- [ ] disabled printer visually distinct from failing printer

## Monitoring rules
- [ ] load current values
- [ ] save changed values
- [ ] values persist after reload
- [ ] rule changes affect runtime behavior globally

## Manual commands
- [ ] M105 works
- [ ] M114 works
- [ ] M115 works
- [ ] M104 with target temperature works
- [ ] M140 with target temperature works
- [ ] M106 works
- [ ] M107 works
- [ ] invalid command rejected
- [ ] missing target temperature rejected

## Events
- [ ] monitoring events visible
- [ ] command success events visible
- [ ] command failure events visible

## Real printer
- [ ] correct current tty used
- [ ] M105 works on real printer
- [ ] failure compared against direct manual serial access
 