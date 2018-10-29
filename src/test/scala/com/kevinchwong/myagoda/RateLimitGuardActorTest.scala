package com.kevinchwong.myagoda

/**
  * Created by kwong on 10/22/18.
  */

import akka.actor._
import akka.pattern._
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import akka.util.Timeout
import com.kevinchwong.myagoda.RateLimitGuardActor.{Request, Response, STATUS_AVAILABLE, STATUS_SUSPENDED}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, _}
import scala.concurrent.duration._
import scala.util._

class RateLimitGuardActorTest
  extends TestKit(
    ActorSystem("TestKitUsageSpec", ConfigFactory.parseString(RateLimitGuardActorTest.config))
  ) with DefaultTimeout
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def beforeAll: Unit = {
  }

  override def afterAll {
    shutdown()
  }

  implicit val ec = ExecutionContext.global
  val logger = LoggerFactory.getLogger(classOf[RateLimitGuardActorTest]);

  "Test for Actor: RateLimitGuardActor" should
    {
      "Sending 1 request - Expected result : get correct response" in
        {
          // The purpose of this test is to verify our rate limit guard can accept a request correctly.
          val st = System.currentTimeMillis()

          implicit val timeout = Timeout(5.minutes)

          val guard = system.actorOf(RateLimitGuardActor.props(apiKey = "423", rateLimitPeriod = 200.millis, suspendingPeriod = 500.millis)
            .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:423")

          val future = guard ? Request

          future.onComplete
          {
            case Success(resp: Response) =>
            {
              resp.apiKey shouldBe ("423")
              resp.pass shouldBe (true)
              resp.status shouldBe (STATUS_AVAILABLE)
            }
            case _ => fail()
          }

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)

          guard ! PoisonPill
          Thread.sleep(1000) //wait 1 seconds and hope that all actors are really cleaned up by ActorSystem
          logger.info("guard:423 are supposed to be cleaned")
        }


      "Creating 101 actors with keys (100-200), recreate twice - Expected result : failed" in
        {
          // The purpose of this test is to verify a actor with same apiKey can not be recreated twice in the actorsystem.

          val st = System.currentTimeMillis()

          implicit val timeout = Timeout(5.minutes)

          val actors = (100 to 200).map(
            i => system.actorOf(RateLimitGuardActor.props(apiKey = i.toString, rateLimitPeriod = 8.millis, suspendingPeriod = 100.millis)
              .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:$i")
          ).toList

          val awaitables = actors.map(
            actor =>
              Future
              {
                actor ? RateLimitGuardActor.Request
              }
          )

          val res = Await.result(Future.sequence(awaitables), 5.minutes)
          logger.debug("{}", res: Any)

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)

          res.length shouldBe (101)

          intercept[akka.actor.InvalidActorNameException]
            {
              try
              {
                {
                  // create 101 actors again with same set of APIKey again.
                  (100 to 200).map(
                    i => system.actorOf(RateLimitGuardActor.props(apiKey = i.toString, rateLimitPeriod = 8.millis, suspendingPeriod = 100.millis)
                      .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:$i")
                  ).toList
                }
              } catch
              {
                case e: akka.actor.InvalidActorNameException =>
                {
                  logger.info("Get akka.actor.InvalidActorNameException as expected")
                  throw e
                }
              }
              finally
              {
                // clean up ActorSystem for next test cases
                val killers = actors.map(
                  actor =>
                    Future
                    {
                      actor ? PoisonPill
                    }
                )
                Await.result(Future.sequence(killers), Duration.Inf)

                Thread.sleep(1000) //wait 1 seconds and hope that all actors are really cleaned up by ActorSystem
                logger.info("all actors are supposed to be cleaned")
              }
            }
        }

      "Creating 101 actors with keys (100-200), Kill them, and recreate again - Expected result : success" in
        {
          // The purpose of this test is to verify a killed actor with a given apiKey can still be recreated if it have been completely removed in the actorsystem.

          val st = System.currentTimeMillis()

          implicit val timeout = Timeout(5.minutes)

          val actors = (100 to 200).map(
            i => system.actorOf(RateLimitGuardActor.props(apiKey = i.toString, rateLimitPeriod = 8.millis, suspendingPeriod = 100.millis)
              .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:$i")
          ).toList

          try
          {
            // Send requests to 101 actors
            val awaitables = actors.map(
              actor =>
                Future
                {
                  actor ? RateLimitGuardActor.Request
                }
            )
            val res = Await.result(Future.sequence(awaitables), 5.minutes)
            logger.debug("{}", res: Any)

            logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)

            res.length shouldBe (101)

            // Kill all actors
            val killers = actors.map(
              actor =>
                Future
                {
                  actor ? PoisonPill
                }
            )
            Await.result(Future.sequence(killers), Duration.Inf)

            Thread.sleep(1000) //wait 1 seconds and hope that all actors are really cleaned up by ActorSystem
            logger.info("all actors are supposed to be cleaned")

            // create new 101 actors again with same set of APIKey.
            val actors2 = (100 to 200).map(
              i => system.actorOf(RateLimitGuardActor.props(apiKey = i.toString, rateLimitPeriod = 8.millis, suspendingPeriod = 100.millis)
                .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:$i")
            ).toList

            val awaitables2 = actors2.map(
              actor =>
                Future
                {
                  actor ? RateLimitGuardActor.Request
                }
            )
            val res2 = Await.result(Future.sequence(awaitables2), Duration.Inf)
            logger.debug("{}", res2: Any)

          }
          finally
          {
            // clean up ActorSystem for next test cases
            val killers = actors.map(
              actor =>
                Future
                {
                  actor ? PoisonPill
                }
            )
            Await.result(Future.sequence(killers), Duration.Inf)
            Thread.sleep(1000) //wait 1 seconds and hope that all actors are really cleaned up by ActorSystem
            logger.info("all actors are supposed to be cleaned")
          }
        }

      "Sending 10 requests, same apiKey - Expected result : Don't trigger the rate limited error" in
        {
          // The purpose of this test is to verify the general functionality/logic of the rate limit guard

          val st = System.currentTimeMillis()

          implicit val timeout = Timeout(5.minutes)

          val guard = system.actorOf(RateLimitGuardActor.props(apiKey = "223", rateLimitPeriod = 4.millis, suspendingPeriod = 10000.millis)
            .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:223")


          (1 to 10).map(
            i=>
              Future{
                Thread.sleep(i*30)
                guard ? RateLimitGuardActor.Request
              }.foreach{f=>
                    logger.debug("f:"+f.toString)
                    for(r<-f){
                      logger.debug("By for(r<-f):"+r.toString)
                      r match {
                        case Response(_,pass,status,_,_)=>(status,pass) shouldBe (STATUS_AVAILABLE,true)
                      }
                    }
              }
          )

          Thread.sleep(10000) // Hold 10 seconds to wait all 10 futures to be completed.

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)

          guard ! PoisonPill

          Thread.sleep(1000) //wait 1 seconds and hope that all actors are really cleaned up by ActorSystem
          logger.info("guard:223 are supposed to be cleaned")

        }

      "Sending 100 requests concurrently, same apiKey, 5 sec timeout - Expected result : either pass or block result only" in
        {
          // The purpose of this test is to verify different situation of rate limit guard

          val st = System.currentTimeMillis()

          implicit val timeout = Timeout(5.minutes)

          val guard = system.actorOf(RateLimitGuardActor.props(apiKey = "223", rateLimitPeriod = 999.millis, suspendingPeriod = 10000.millis)
            .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:223")

          (1 to 100).map(
            i=>
              Future{
                guard ? RateLimitGuardActor.Request
              }.foreach{f=>
                logger.debug("f:"+f.toString)
                for(r<-f){
                  logger.debug("By for(r<-f):"+r.toString)
                  r match {
                    case Response(_,pass,status,_,_)=>(status,pass) should (be(STATUS_AVAILABLE,true) or be(STATUS_SUSPENDED,false))
                  }
                }
              }
          )

          Thread.sleep(5000) // Hold 5 seconds to wait all 10 futures to be completed.

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)

          guard ! PoisonPill

          Thread.sleep(1000) //wait 1 seconds and hope that all actors are really cleaned up by ActorSystem
          logger.info("guard:223 are supposed to be cleaned")

        }

      "Sending 4 requests, same apiKey, try to hit the rate limit - Expected result : the guard get into suspending status for 500 millis" in
        {
          // The purpose of this test is to verify the general functionality/logic of the rate limit guard

          val st = System.currentTimeMillis()

          implicit val timeout = Timeout(5.minutes)

          val guard = system.actorOf(RateLimitGuardActor.props(apiKey = "323", rateLimitPeriod = 200.millis, suspendingPeriod = 500.millis)
            .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:323")

          logger.info("Sending 1st Request")
          val res1 = Await.result(guard ? RateLimitGuardActor.Request, Duration.Inf)
          Thread.sleep(10)

          logger.info("Sending 2nd Request, and the guard will be turned as Suspended status")
          val res2: Response = Await.result(guard ? RateLimitGuardActor.Request, Duration.Inf).asInstanceOf[Response]
          res2.status shouldBe (STATUS_SUSPENDED)

          logger.info("Sending 3rd Request, and the guard will keep as Suspended status")
          val res3: Response = Await.result(guard ? RateLimitGuardActor.Request, Duration.Inf).asInstanceOf[Response]
          res3.status shouldBe (STATUS_SUSPENDED)

          Thread.sleep(500)
          logger.info("Sending 4th Request, and the guard should be over the Suspended status, get back to Available")
          val res4: Response = Await.result(guard ? RateLimitGuardActor.Request, Duration.Inf).asInstanceOf[Response]
          res4.status shouldBe (STATUS_AVAILABLE)

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)

          guard ! PoisonPill

          Thread.sleep(1000) //wait 1 seconds and hope that all actors are really cleaned up by ActorSystem
          logger.info("guard:323 are supposed to be cleaned")

        }

      "Sending 10000 requests, 5 seconds timeout - Expected result : # of incompleted future > 0 , reason: mailbox overflow" in
        {
          // The purpose of this test is to simulate the case of mailboxes overflow in actor.
          // sender will get timeout since the message has been redirected to dead letter queue.

          // In normal situation, there will have no 1000+ requests coming to same actor in an instant
          // Just in case, if there are a lot of requests, the overflowed messages hitting the rate limited.

          // Messages go to dead letter queue is not a bad situation, we can just quickly determine it cannot pass the rate limit checker

          // Since actor is non-blocking a process, we assume it can handle each message just in a few milliseconds,
          // and actor will be keeping clean up the messages in mailbox, even through there are some messages has been redirected to Dead Letter queue.
          // So the next incoming message will still have chance to enter the mailbox, instead of going to deadletter queue.

          val st = System.currentTimeMillis()

          implicit val timeout = Timeout(5.minutes)

          val guard = system.actorOf(RateLimitGuardActor.props(apiKey = "423", rateLimitPeriod = 200.millis, suspendingPeriod = 500.millis)
            .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:423")

          val resps = Await.result(
            Future.sequence((1 to 10000).map(i =>
              Future
              {
                (guard ? Request)
              })), 5.seconds)

          val incompletedActorMessages = resps.filter(f=>f.isCompleted==false).length

          incompletedActorMessages should be > 0

          logger.info("time used : {} millis", System.currentTimeMillis() - st: Any)

          guard ! PoisonPill

          Thread.sleep(1000) //wait 1 seconds and hope that all actors are really cleaned up by ActorSystem
          logger.info("guard:423 are supposed to be cleaned")

        }

    }
}

object RateLimitGuardActorTest {

  // Define your test specific configuration here

  val config =

    """
    my-bounded-priority-mailbox {
      mailbox-type = "com.kevinchwong.myagoda.MyPriorityActorMailbox"
    }
    akka{
      log-level="INFO"
      log-dead-letters=off
      log-dead-letters-during-shutdown=off
    }
  """

}