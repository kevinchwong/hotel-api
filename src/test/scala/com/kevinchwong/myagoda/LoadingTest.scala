package com.kevinchwong.myagoda

/**
  * Created by kwong on 10/22/18.
  */

import akka.actor._
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.google.gson.Gson
import com.kevinchwong.myagoda.RateLimitGuardActor.{Response, STATUS_AVAILABLE, STATUS_SUSPENDED}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory
import scala.concurrent._
import scala.concurrent.duration._
import scalaj.http.Http

class LoadingTest
  extends TestKit(
    ActorSystem("TestKitUsageSpec", ConfigFactory.parseString(LoadingTest.config))
  ) with DefaultTimeout
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def beforeAll: Unit = {
    MainHttpServer.server.start()
  }

  override def afterAll {
    RateLimitService.terminate
    shutdown()
  }

  implicit val ec = ExecutionContext.global
  val logger = LoggerFactory.getLogger(classOf[LoadingTest]);

  "Test for Service: RateLimitService" should
    {
      // The purpose of this test is to verify the general functionality of RateLimitService.allow() in extreme cases:

      "loading test - 100000 concurrent requests, same apiKey " in {

        val loadSize=100000

        // Reset configuration before each test
        RateLimitService.cleanAllActors
        Thread.sleep(500)
        Settings.loadConfig("config.json")
        Settings.globalDefaultRateLimitPeriod = 50.millis
        Settings.suspendingPeriod = 10.minute
        Settings.suspendDecisionTimeout = 100.millis

        val st = System.currentTimeMillis()
        val awaitables = (1 to loadSize).map(i =>
          Future
          {
            logger.info("Sending Request " + i.toString)
            RateLimitService.allow("1237").asInstanceOf[Response]
          }
        )

        val resps = Await.result(Future.sequence(awaitables), Duration.Inf)

        resps.count(resp => resp.status == STATUS_AVAILABLE).shouldBe(1)
        resps.count(resp => resp.status == STATUS_SUSPENDED).shouldBe(loadSize-1)

        logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)
        RateLimitService.cleanAllActors
        Thread.sleep(1000)
      }

      "1000 requests sent with 200 millis in-between, 50 millis rate limit period - expected all with code 200" in
        {
          // The purpose of this test is to verify the general functionality of our HTTP servers and components in extreme cases:

          val loadSize=1000

          // Reset configuration before each test
          RateLimitService.cleanAllActors
          Thread.sleep(500)
          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod=50.millis
          Settings.suspendingPeriod=100.millis

          // Send the post request

          val resps =
          for(i<-1 to loadSize) yield {
            Thread.sleep(Settings.suspendingPeriod.toMillis+100)
            val r = Http("http://localhost:8000/hotel/api/")
              .postData(Tool.gson.toJson(new MyRequest((123).toString, (i%27).toString)))
              .header("content-type", "application/json")
              .asString
            logger.debug(r.toString)
            r.code shouldBe (200)
            r
          }
          //println(resps)

        }

    }

}

object LoadingTest {

  // Define your test specific configuration here

  val config =

    """
    akka{
      log-level="ERROR"
      log-dead-letters=off
      log-dead-letters-during-shutdown=off
    }
    """
}