# Hotel API (Home Assignment)

### 1. Quick commands:
```
Run
====
sbt run

Test
====
sbt "test:testOnly *MainHttpServerTest"
sbt "test:testOnly *RateLimitGuardActorTest"
sbt "test:testOnly *RateLimitServiceTest"
sbt "test:testOnly *LoadingTest"
sbt "test:testOnly *HotelDBTest"
```


### 2. Introduction

There are 4 objects working together in this HTTP server to implement our rate limit checking feature:
- A Http server : MainHttpServer
- A service : RateLimitService
- A lot of actors : RateLimitGuardActor
- And a data source : HotelDB

When a new request containing an apiKey comes to our *MainHTTPserver*, we will check if it should be blocked by asking *RateLimitService* through the function call *allow(apiKey)*.

Since the rate limit checking logic of each apiKey work independently, we use one *RateLimitGuardActor* for each apiKey to handle its own checking logic and statitsic. 
And each actor are independent and they work concurrently under the control of ActorSystem.

*RateLimitService* can be considered as the manager of all actors, he used them to get the real time rate limit statistic and logicial decision.
And he is also an adviser of *MainHTTPserver*, because he can tell HTTPserver to block a request or not by providing them the Response object of *allow(apiKey)* call.

*MainHTTPServer* is using multi-thread execution pool, so it can also handle several POST requests concurrently.
If the server gets the permission from *RateLimitService*, it will return the result data by querying from *HotelDB*.


### Files
You can change :-
- HTTP Server configuration in config.json    
- Logging configuration in simplelogger.properties
- Hotel Table data in hoteldb.csv

##### config,json
```javascript
{
  "settings":{
    "port":8000,
    "numberOfThread":20,
    "globalDefaultRateLimitPeriod":"10 seconds", <--- //default is 10 seconds.
    "rateLimitPeriodMap": {
      "apiKey9123": "50 millis",                 <--- //new period overrides the default. 
      "apiKey9124": "2 seconds",
      "apiKey9125": "250 millis",
      "apiKey9126": "50 millis",
      "apiKey9127": "550 millis",
      "apiKey9128": "100 millis",
      "apiKey9224": "8 millis"
    },
    "suspendingPeriod":"5 minutes",
    
    "actorTimeoutLimit":"5 minutes",        <-- //implicit timeout for actor internal response process
    "suspendDecisionTimeout":"100 millis",  <-- //if the sender cannot get response from the actor in this timeout,
                                                //allow() will return pass=false
    
    "enableActorCleaner":true,              <----//for actors cleaner only
    "cleanerInbetweenPeriod":"24 hour",     <---//
    "cleanerGracePeriod":"1 minutes"        <--//
  //
}
```

### 3. How to run HTTPServer 
##### Command: sbt run
```commandline
$ sbt run

MY HOTEL API -- (HttpServer with Rate Limit features)
Written by Kevin C. Wong
in October 2018

 Configuration Information 
 ================================================================================
 Running on port                  : 8000
 Number of Threads                : 20
 Global Default Rate Limit Period : 10 seconds
 Suspending Period                : 5 minutes
 Periodical Actors Cleaner        : ON
 Cleaner in-between Period        : 1 day
 Cleaner Grace Period             : 1 minute

Server has been started. Now, you can send your URL post request to http:/10.0.0.245:8000/hotel/api/
Press ctrl-C to exit

2018/10/28 04:02:04-674 [scala-execution-context-global-46] [INFO] RateLimitService$ - Start periodicial actors cleaning
```


### Post Request
I tested the server with PostMan
##### URL
```
http://localhost:8000/hotel/api/
```
##### Post data 
```json
{
	"apiKey" : "87423847",
	"cityId" : "1"
}
```
##### Output
```json
{
  "city": "Bangkok",
  "cityId": "1",
  "room": "Deluxe",
  "price": 1000.0,
  "status": "Ok!"
}
```


### 4. Unit Tests

#### 4.1 MainHttpServerTest
Our main program is MainHttpServer.scala

The purpose of this test is to verify the functionality of various http post API call
##### Command : sbt "test:testOnly *MainHttpServerTest"  
```commandline
$ sbt "test:testOnly *MainHttpServerTest" 
[info] Loading global plugins from /Users/kevinwong/.sbt/0.13/plugins
[info] Loading project definition from /Users/kevinwong/myCode/rateLimitHttpServer/project
[info] Set current project to rateLimitHttpServer (in build file:/Users/kevinwong/myCode/rateLimitHttpServer/)
[info] Compiling 1 Scala source to /Users/kevinwong/myCode/rateLimitHttpServer/target/scala-2.12/test-classes...
[warn] there were 6 feature warnings; re-run with -feature for details
[warn] one warning found
[info] MainHttpServerTest:
[info] Test for HttpServer with post call
[info] - should Sending 1 request properly - Excepted result : correct result
[info] - should Sending 1 request with a non existing key - Excepted result : No Hotel found!
[info] - should Sending 1 request with wrong port - Excepted result : ConnectException error
[info] - should Sending 1 request empty API key - Excepted result : No Hotel found!
[info] - should Sending 1 request with no API key - Expected Result : No apiKey in input!
[info] - should Sending 1 request with bad post data - Excepted result : Input is not in correct Json format!
[info] - should Sending 10 requests concurrently with different apiKeys - Expected Result : all with code 200
[info] - should Sending 10 requests concurrently with same apikey - Expected results : one code is 200, others are 429
[info] - should Sending 10+1 requests, same apikey, triggered suspending period, then sending last one after suspending period - Expected results : last request with code 200
[info] - should Sending 20 requests with 100 millis in-between gap, 50 millis rate limit period - Expected results : all with code 200
[info] Run completed in 28 seconds, 497 milliseconds.
[info] Total number of tests run: 10
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 10, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 46 s, completed Oct 28, 2018 5:16:38 AM

```

#### 4.2. HotelDBTest
HotelDB is our main data source
The purpose of this test is to verify the query functions of HotelDB.
##### Command : sbt "test:testOnly *HotelDBTest" 
```commandline
$ sbt "test:testOnly *HotelDBTest" 
[info] Loading global plugins from /Users/kevinwong/.sbt/0.13/plugins
[info] Loading project definition from /Users/kevinwong/myCode/rateLimitHttpServer/project
[info] Set current project to rateLimitHttpServer (in build file:/Users/kevinwong/myCode/rateLimitHttpServer/)
[info] HotelDBTest:
[info] Test the HotelDB and csv files
[info] - should HotelDB.query("1") - expected pass
[info] - should HotelDB.query("") - expected NoSuchElementException
[info] - should HotelDB.query("badKey") - expected NoSuchElementException
[info] - should HotelDB.query(null) - expected NoSuchElementException
[info] - should HotelDB.queryJson("2") - expected pass
[info] - should HotelDB.queryJson("")  - expected NoSuchElementException
[info] - should HotelDB.queryJson("badKey")  - expected NoSuchElementException
[info] - should HotelDB.queryJson("null")  - expected NoSuchElementException
[info] Run completed in 2 seconds, 148 milliseconds.
[info] Total number of tests run: 8
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 8, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 6 s, completed Oct 28, 2018 6:52:48 AM

```

#### 4.3. RateLimitServiceTest
The main function of RateLimitService is allow(spiKey:String).
The purpose of this test is to verify whether we can use allow() to determine a new request will be passed or blocked by the rate limit checking logic in different situation.
##### Command : sbt "test:testOnly *RateLimitServiceTest" 
```commandline
$ sbt "test:testOnly *RateLimitServiceTest"
[info] Loading global plugins from /Users/kevinwong/.sbt/0.13/plugins
[info] Loading project definition from /Users/kevinwong/myCode/rateLimitHttpServer/project
[info] Set current project to rateLimitHttpServer (in build file:/Users/kevinwong/myCode/rateLimitHttpServer/)
[info] RateLimitServiceTest:
[info] Test for Service: RateLimitService
[info] - should Sending 4 requests, same apiKey, try to hit the rate limit - Expected result :  the guard get into suspending status for 100 millis
[info] - should Sending 100 concurrent requests, same apiKey - Expected result : RateLimitService allow() can block the latter reuqests properly
[info] - should Sending 1001 concurrent requests, same apiKey - Expected result : last request will be sent after the suspending period is over
[info] Run completed in 19 seconds, 91 milliseconds.
[info] Total number of tests run: 3
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 23 s, completed Oct 28, 2018 8:15:26 AM


```

#### 4.4. RateLimitGuardActorTest
The rate limit checking logic is defined in each RateLimitGuard Actor.
One RateLimitGuard actor is assigned for each ApiKey.
The purpose of this test is to verify the rate limit checking logic in different situation.
##### Command : sbt "test:testOnly *RateLimitGuardActorTest" 
```commandline
$ sbt "test:testOnly *RateLimitGuardActorTest" 
[info] Loading global plugins from /Users/kevinwong/.sbt/0.13/plugins
[info] Loading project definition from /Users/kevinwong/myCode/rateLimitHttpServer/project
[info] Set current project to rateLimitHttpServer (in build file:/Users/kevinwong/myCode/rateLimitHttpServer/)
[info] Compiling 1 Scala source to /Users/kevinwong/myCode/rateLimitHttpServer/target/scala-2.12/test-classes...
[info] RateLimitGuardActorTest:
[info] Test for Actor: RateLimitGuardActor
[info] - should Sending 1 request - Expected result : get correct response
[info] - should Creating 101 actors with keys (100-200), recreate twice - Expected result : failed
[info] - should Creating 101 actors with keys (100-200), Kill them, and recreate again - Expected result : success
[info] - should Sending 10 requests, same apiKey - Expected result : Don't trigger the rate limited error
[info] - should Sending 100 requests concurrently, same apiKey, 5 sec timeout - Expected result : either pass or block result only
[info] - should Sending 4 requests, same apiKey, try to hit the rate limit - Expected result : the guard get into suspending status for 500 millis
[info] - should Sending 10000 requests, 5 seconds timeout - Expected result : # of incompleted future > 0 , reason: mailbox overflow
[info] Run completed in 27 seconds, 92 milliseconds.
[info] Total number of tests run: 7
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 7, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 33 s, completed Oct 28, 2018 8:12:42 AM


```

#### 4.5. LoadingTest
The purpose of this test is to verify our server when we increase the load size.
##### Command : sbt "test:testOnly *LoadingTest" --error
```commandline
$ sbt "test:testOnly *LoadingTest" --error
[success] Total time: 231 s, completed Oct 28, 2018 6:57:34 AM
```

### 5. Notes
- You can also check the detail of the purpose of every test cases in the comments inside each source code files.
- If you have any questions, please feel free to drop me a message : kevinchwong@gmail.com
