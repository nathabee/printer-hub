# TEST CASE

---

## CASE1 : KO


 i go in prepare => ask for auto home. (G28) => no event created, no hoistory, and in the trace i see [PrinterHub] Printers: http://localhost:18080/printers
[PrinterHub] Settings: http://localhost:18080/settings/monitoring
[PrinterHub] API operation failed: No response for command 'G28' on /dev/ttyUSB0
## but really interessant my printer is changing and making the move to go home (so communication to printer work)

so 2 problem :
* when a problem is detected nothing is logged in the event and diagnostic (should already have been done in 0.2.2 and 0.2.3) no acknoledgment and log from error in the event or diagnostiy 
* see a problem but in fact there is no problem because the G28 command is working

---

## CASE2 : KO

i go in print => create a job => read position=> i do not see  the answer there # andwer is in JOB_SUCEED event, this is the event that should be displayed clearly because it contains the printer answer, in the 


---
## CASE3 : OK

create a new simulation printer sim and delete
creation possible
enable possible
read monitoring works
disable possible
delete possible

same with real printer


---
## CASE4 :

real printer => print => create a job "READ_FIRMWARE_INFO"
* after create a job : created date available,  started and finiseh not available
* ???? load history : nothing (i was expecting creation event), status ASSIGNED
* it can be seen the same way o "(all) printers"=> (all) jobs, same without history (normal????)

* after start : 
- start and finish date are filled
- no answer to be seen (???we are exoecting the result of the job)
- even after clicking on "history/ load ebvents"  and load diagnostic not thing shown (error, like the load button are not working in the job card/view)
- if i go in the "history" menu, then i see the result : in the executon diagnostics for printer jobs (response is shown, commandm outxome and failure : exactly what i wanted to se in the job card of the print menu) # in the history i see in the history/printer events : events no loaded yet, so i click on load event=> and there the correct question to the backend is done and i see the events associated with the job (good)###
 so i go back to the print menu visualize the job again, and load history and oad diagnostic still not working####### 
 si oi go back in the printer history, go on job hostory for this printer , select the job and click on load history and load events for the job (what is should do???)