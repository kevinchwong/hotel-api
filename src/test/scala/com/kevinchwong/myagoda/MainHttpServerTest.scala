package com.kevinchwong.myagoda

/**
  * Created by kwong on 10/22/18.
  */

import java.net.ConnectException

import akka.actor._
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.concurrent.duration._
import scalaj.http.Http

class MyRequest(val apiKey: String, val cityId: String) {
  override def toString = apiKey + ", " + cityId
}

class MainHttpServerTest
  extends TestKit(
    ActorSystem("TestKitUsageSpec", ConfigFactory.parseString(MainHttpServerTest.config))
  ) with DefaultTimeout
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def beforeAll {
    MainHttpServer.server.start
  }

  override def afterAll {
    MainHttpServer.terminate
    shutdown()
  }

  implicit val ec = ExecutionContext.global
  val logger = LoggerFactory.getLogger(classOf[MainHttpServerTest]);

  "Test for HttpServer with post call" should
    {
      // The purpose of this test is to verify the functionality of a general http post in a postive case:

      "Sending 1 request properly - Excepted result : correct result" in
        {

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=500.millis
          Thread.sleep(Settings.suspendingPeriod.toMillis)

          // Send the post request
          val future = Future
          {
            Http("http://localhost:8000/hotel/api/")
              .postData(Tool.gson.toJson(new MyRequest("MyApiKey1234567", "18")))
              .header("content-type", "application/json").asString
          }
          val resp=Await.result(future,1 minute)

          logger.debug("resp.body is {}",resp.body:Any)

          Tool.gson.fromJson(resp.body,classOf[Any]) shouldBe(Tool.gson.fromJson("""{
            "city": "Bangkok",
            "cityId": "18",
            "room": "Sweet Suite",
            "price": 5300.0,
            "status": "Ok!"
          }""",classOf[Any]))

        }

      "Sending 1 request with a non existing key - Excepted result : No Hotel found!" in
        {
          // The purpose of this test is to verify the functionality of a general http post in a negative case:

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=500.millis

          // Send the post request
          val future = Future
          {
            Http("http://localhost:8000/hotel/api/")
              .postData(Tool.gson.toJson(new MyRequest("MyApiKey1234567", "notexistkey")))
              .header("content-type", "application/json").asString
          }
          val resp=Await.result(future,1 minute)

          logger.debug("resp.body is {}",resp.body:Any)

          Tool.gson.fromJson(resp.body,classOf[Any]) shouldBe(Tool.gson.fromJson("""{"status"="No Hotel found!"}""",classOf[Any]))
        }

      "Sending 1 request with wrong port - Excepted result : ConnectException error" in
        {
          // The purpose of this test is to verify the situation of a bad port number in url:

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=500.millis

          // Send the post request
          val future = Future
          {
            Http("http://localhost:1234/")
              .postData(Tool.gson.toJson(new MyRequest("MyApiKey1234567", "notexistkey")))
              .header("content-type", "application/json").asString
          }
          intercept[ConnectException]
            {
              Await.result(future, 1 minute)
            }
        }

      "Sending 1 request empty API key - Excepted result : No Hotel found!" in
        {
          // The purpose of this test is to verify the situation of a empty API Key in the post data:

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=500.millis

          // Send the post request
          val future = Future
          {
            Http("http://localhost:8000/hotel/api/")
              .postData(Tool.gson.toJson(new MyRequest("", "notexistkey")))
              .header("content-type", "application/json").asString
          }
          val resp=Await.result(future,1 minute)

          logger.debug("resp.body is {}",resp.body:Any)

          Tool.gson.fromJson(resp.body,classOf[Any]) shouldBe(Tool.gson.fromJson("""{"status"="No Hotel found!"}""",classOf[Any]))
        }

      "Sending 1 request with no API key - Expected Result : No apiKey in input!" in
        {
          // The purpose of this test is to verify the situation of missing API Key in the post data:

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=500.millis

          // Send the post request
          val future = Future
          {
            Http("http://localhost:8000/hotel/api/")
              .postData(
                """
                  {
                    "cityId" : "1"
                  }
                """
              )
              .header("content-type", "application/json").asString
          }
          val resp=Await.result(future,1 minute)

          logger.debug("resp.body is {}",resp.body:Any)

          Tool.gson.fromJson(resp.body,classOf[Any]) shouldBe(Tool.gson.fromJson("""{"status"="No apiKey in input!"}""",classOf[Any]))
        }

      "Sending 1 request with bad post data - Excepted result : Input is not in correct Json format!" in
        {
          // The purpose of this test is to verify the situation of a non-json format post data:

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=500.millis

          // Send the post request
          val future = Future
          {
            Http("http://localhost:8000/hotel/api/")
              .postData(
                """
                  NOT in Json Format
                """
              )
              .header("content-type", "application/json").asString
          }
          val resp=Await.result(future,1 minute)

          logger.debug("resp.body is {}",resp.body:Any)

          Tool.gson.fromJson(resp.body,classOf[Any]) shouldBe(Tool.gson.fromJson("""{"status": "Input is not in correct Json format!"}""",classOf[Any]))
        }

      "Sending 10 requests concurrently with different apiKeys - Expected Result : all with code 200" in
        {
          // The purpose of this test is to verify our server can handle multi requests concurrently with different api keys:

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=500.millis

          // Send the post request
          val futures = (1 to 10).map(i =>
            Future
            {
              Http("http://localhost:8000/hotel/api/")
                .postData(Tool.gson.toJson(new MyRequest((123 + i).toString, i.toString)))
                .header("content-type", "application/json")
                .asString
            }
          )

          val resps = Await.result(Future.sequence(futures), 20.seconds)

          resps.toStream.map(httpResp =>
            httpResp.code
          ).sorted.toSeq shouldBe Stream(200, 200, 200, 200, 200, 200, 200, 200, 200, 200)

        }

      "Sending 10 requests concurrently with same apikey - Expected results : one code is 200, others are 429" in
        {
          // The purpose of this test is to verify our server can block multiple latter requests sent concurrently with same api keys:

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=1.minutes

          // Send the post request
          val futures = (1 to 10).map(i =>
            Future
            {
              Http("http://localhost:8000/hotel/api/")
                .postData(Tool.gson.toJson(new MyRequest((122).toString, i.toString)))
                .header("content-type", "application/json")
                .asString
            }
          )

          val resps = Await.result(Future.sequence(futures), 20.seconds)

          resps.toStream.map(httpResp =>
            httpResp.code
          ).sorted.toSeq shouldBe Stream(200, 429, 429, 429, 429, 429, 429, 429, 429, 429)

        }

      "Sending 10+1 requests, same apikey, triggered suspending period, then sending last one after suspending period - Expected results : last request with code 200" in
        {
          // The purpose of this test is to verify our server can accept the last request after suspending period:

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=10.millis
          Settings.suspendingPeriod=15.seconds

          // Send the post request
          val futures = (1 to 10).map(i =>
            Future
            {
              Http("http://localhost:8000/hotel/api/")
                .postData(Tool.gson.toJson(new MyRequest((123).toString, i.toString)))
                .header("content-type", "application/json")
                .asString
            }
          )

          val resps = Await.result(Future.sequence(futures), 20.seconds)

          resps.toStream.map(httpResp =>
            httpResp.code
          ).sorted.toSeq shouldBe Stream(200, 429, 429, 429, 429, 429, 429, 429, 429, 429)

          Thread.sleep(Settings.suspendingPeriod.toMillis)

          val lastResp = Http("http://localhost:8000/hotel/api/")
            .postData(Tool.gson.toJson(new MyRequest((123).toString, "11")))
            .header("content-type", "application/json")
            .asString
          lastResp.code shouldBe (200)
        }

      "Sending 20 requests with 100 millis in-between gap, 50 millis rate limit period - Expected results : all with code 200" in
        {
          // The purpose of this test is to verify our server can accept a sequence of post requests with a period gap (100 millis) in between:

          // More test cases with large amount of requests can be found in Loading Test Scala

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=50.millis
          Settings.suspendingPeriod=500.millis

          // Send the post request
          val futures = (1 to 20).map(i =>
            Future
            {
              Thread.sleep(i*100)
              Http("http://localhost:8000/hotel/api/")
                .postData(Tool.gson.toJson(new MyRequest((124).toString, i.toString)))
                .header("content-type", "application/json")
                .asString
            }
          )

          val resps = Await.result(Future.sequence(futures), Duration.Inf)

          resps.map(httpResp =>
            httpResp.code
          ).distinct.toSeq shouldBe Stream(200)

        }

    }

}

object MainHttpServerTest {

  // Define your test specific configuration here

  val config =

    """

    """
}