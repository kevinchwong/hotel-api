package com.kevinchwong.myagoda

/**
  * Created by kwong on 10/22/18.
  */

import akka.actor._
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.kevinchwong.myagoda.RateLimitGuardActor.{Response, STATUS_AVAILABLE, STATUS_SUSPENDED}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.concurrent.duration._

class RateLimitServiceTest
  extends TestKit(
    ActorSystem("TestKitUsageSpec", ConfigFactory.parseString(RateLimitServiceTest.config))
  ) with DefaultTimeout
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def beforeAll: Unit = {
    RateLimitService.startPeriodicalCleaner
  }

  override def afterAll {
    RateLimitService.cleanAllActors
    RateLimitService.terminate
    shutdown()
  }

  implicit val ec = ExecutionContext.global
  val logger = LoggerFactory.getLogger(classOf[RateLimitGuardActorTest]);

  "Test for Service: RateLimitService" should
    {
      "Sending 4 requests, same apiKey, try to hit the rate limit - Expected result :  the guard get into suspending status for 100 millis" in
        {
          // The purpose of this test is to verify general functionality of our RateLimitService function:
          // RateLimitService.allow(apiKey:String)

          val st = System.currentTimeMillis()

          RateLimitService.cleanAllActors

          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod = 50.millis
          Settings.suspendingPeriod = 100.millis

          logger.info("Sending 1st Request")
          val res1 = RateLimitService.allow("1234").asInstanceOf[Response]
          Thread.sleep(100)
          logger.info("Sending 2nd Request")
          val res2 = RateLimitService.allow("1234").asInstanceOf[Response]
          logger.info("Sending 3rd Request")
          val res3 = RateLimitService.allow("1234").asInstanceOf[Response]
          Thread.sleep(200)
          logger.info("Sending 4th Request")
          val res4 = RateLimitService.allow("1234").asInstanceOf[Response]

          res1.status shouldBe STATUS_AVAILABLE
          res2.status shouldBe STATUS_AVAILABLE
          res3.status shouldBe STATUS_SUSPENDED
          res4.status shouldBe STATUS_AVAILABLE

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)
        }

      "Sending 100 concurrent requests, same apiKey - Expected result : RateLimitService allow() can block the latter reuqests properly" in
        {
          // The purpose of this test is to verify allow() can block the latter requests within a long suspending period (5 minutes):

          RateLimitService.cleanAllActors

          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod = 50.millis
          Settings.suspendingPeriod = 5.minutes  // should be long enough for this testing

          val st = System.currentTimeMillis()

          val awaitables = (1 to 100).map(i =>
            Future
            {
              logger.info("Sending Request " + i.toString)
              RateLimitService.allow("1235").asInstanceOf[Response]
            }
          )

          val resps = Await.result(Future.sequence(awaitables), Duration.Inf)

          resps.count(resp => resp.status == STATUS_AVAILABLE).shouldBe(1)
          resps.count(resp => resp.status == STATUS_SUSPENDED).shouldBe(99)

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)
        }

      "Sending 1001 concurrent requests, same apiKey - Expected result : last request will be sent after the suspending period is over" in
        {
          // The purpose of this test is to verify allow() can even accept the request after suspending period (10 seconds):

          RateLimitService.cleanAllActors

          Settings.loadConfig("config.json")
          Settings.globalDefaultRateLimitPeriod = 50.millis
          Settings.suspendingPeriod = 10.seconds
          Settings.suspendDecisionTimeout = 100.millis

          val st = System.currentTimeMillis()

          val awaitables = (1 to 1001).map(i =>
          {
            i match
            {
              case 1001 =>
                Future
                {
                  Thread.sleep(15000)
                  logger.info("Sending last Request " + i.toString)
                  RateLimitService.allow("1236").asInstanceOf[Response]
                }
              case _ =>
                Future
                {
                  logger.info("Sending Request " + i.toString)
                  RateLimitService.allow("1236").asInstanceOf[Response]
                }
            }
          })

          val resps = Await.result(Future.sequence(awaitables), Duration.Inf)

          resps.count(resp => resp.status == STATUS_AVAILABLE).shouldBe(2)
          resps.count(resp => resp.status == STATUS_SUSPENDED).shouldBe(999)

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)
        }

    }
}

object RateLimitServiceTest {

  // Define your test specific configuration here

  val config =

    """
    """
}