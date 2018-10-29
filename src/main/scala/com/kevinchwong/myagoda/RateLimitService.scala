package com.kevinchwong.myagoda

import java.util.concurrent.ConcurrentHashMap

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.pattern.ask
import akka.util.Timeout
import com.kevinchwong.myagoda.RateLimitGuardActor.{Request, Response, STATUS_SUSPENDED}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


object RateLimitService {

  val logger = LoggerFactory.getLogger(RateLimitService.getClass);

  // Service contains :

  // 1. an actor system,
  val system: ActorSystem = ActorSystem("RateLimitService-" + System.currentTimeMillis(), ConfigFactory.load)
  // 2. one concurrentHashMaps for storing assigned actor for each apiKey.
  val guardActorRefMap: ConcurrentHashMap[String, ActorRef] = new ConcurrentHashMap()
  // 3. one concurrentHashMaps for storing the last access time of the actor for each apiKey. (used by the periodic cleaner only)
  val guardLastAccessTimeMap: ConcurrentHashMap[String, Long] = new ConcurrentHashMap()

  def allow(apiKey: String): Any = {

    // save the last access time grouped by same API Key
    guardLastAccessTimeMap.put(apiKey, System.currentTimeMillis())

    // finalize the properties from different sources
    val myRateLimitPeriod = Duration(Settings.rateLimitPeriodMap.getOrDefault(apiKey, Settings.globalDefaultRateLimitPeriod.toString).toString)
    val mySuspendingPeriod = Settings.suspendingPeriod

    // MAIN LOGIC
    implicit val timeout = Timeout(Settings.actorTimeoutLimit)

    // 1. Find the rateLimitGuard actor by the apiKey
    val rateLimitGuard = this.synchronized
    {
      if (!guardActorRefMap.containsKey(apiKey))
      {
        guardActorRefMap.put(apiKey, system.actorOf(RateLimitGuardActor.props(apiKey, myRateLimitPeriod, mySuspendingPeriod)
          .withMailbox("my-bounded-priority-mailbox"), s"rateLimitGuard:$apiKey"))
      }
      guardActorRefMap.get(apiKey)
    }

    // 2. send actor (RateLimitGuardActor) a request to ask for its Status: (Available of Suspended)
    val future = rateLimitGuard ? Request

    try
    {
      // Normally, the actor is non-blocking and we assume we can get the result very fast (within suspendDecisionTimeout)
      val res = Await.result(future, Settings.suspendDecisionTimeout)
      logger.debug("Service call result :- {}", res: Any)
      res
    } catch
    {
      // But... if timeout occurs, we just consider this is a case of SUSPENDED
      //
      // 'Timeout' mostly occurs due to one of below situations:
      // 1. Mailbox is overflowed (our MailBox size is 10)
      // 2. The actor takes long time to process its message
      // 3. The server is running too slow.
      // 4. In the period of that the actor is terminating or terminated, but the new replacement has been be created yet.
      //
      // For 1,2,3 we can return a SUSPENDED response status since the server is really busy.
      //
      // For 4, it is only happened if we turned on 24 hours ActorsCleaning feature.
      //
      //        When a new request comes while the actor is terminating, the message can be or can not excess the rate limit.
      //
      //        Honestly, if this is a good message and we want to keep handling it in a new reborn actor, there will be more work to do.
      //        we can grab the dead letter message one by one from the dead letter queue, and reborn the Actor
      //        for the first message among its group with same ApiKey.
      //        Also, we need to redirect the remaining dead letters from the queue to the reborn actor with same ApiKey.
      //
      //        But before we implement this, we can go back one step...
      //
      //        The reason we want to give this actor a poison pill is just because we have thought that this actor is less frequency
      //        used after comparing its last access time.
      //
      //
      case e: java.util.concurrent.TimeoutException => new Response(apiKey, false, STATUS_SUSPENDED, System.currentTimeMillis, None)
    }
  }

  def terminate() = {
    Await.result(system.terminate, Duration.Inf)
  }

  // (Optional features) ActorCleaners :
  // Just use it
  // 1. Cleanup those unused actors periodically.
  //    check these properties in config.json
  //     -> "enableActorCleaner":true,
  //     -> "cleanerInbetweenPeriod":"1 hour",
  //     -> "cleanerGracePeriod":"1 minutes"
  //
  // 2. Cleanup all actors before each unit test cases.
  //    For example: RateLimitService.cleanAllActors, all actors will be cleared out by this async call. (grace period = 0 seconds)
  //

  def startPeriodicalCleaner: Unit = {
    implicit val ec = ExecutionContext.global
    if (Settings.enableActorCleaner)
    {
      system.scheduler.schedule(0.millis, Settings.cleanerInbetweenPeriod)
      {
        periodicalCleanActors
      }
    }
  }

  def periodicalCleanActors(): Unit = {
    logger.info("Start periodicial actors cleaning")
    cleanActors(None)
  }

  def cleanAllActors(): Unit = {
    cleanActors(Some(0.seconds))
  }

  def cleanActors(winAllResetGracePeriod: Option[Duration]): Unit = {

    val myResetGracePeriod = (winAllResetGracePeriod.isDefined) match
    {
      case true => winAllResetGracePeriod.get
      case false => Settings.cleanerGracePeriod
    }
    val currentTime: Long = System.currentTimeMillis

    // Logic of the cleaner
    // - Just check all apiKey and its actors' last access time.
    // - If this different with current time is over myResetGracePeriod, we will kill this actor
    this.synchronized
    {
      implicit val ec = ExecutionContext.global
      implicit val timeout = Timeout(Settings.actorTimeoutLimit)

      logger.debug("cleanActors - Before cleaning...")
      logger.debug("cleanActors - currentTime : {} ", currentTime: Any)
      logger.debug("cleanActors - map : {} ", guardLastAccessTimeMap: Any)
      logger.debug("cleanActors - grace period used : {} ", myResetGracePeriod: Any)

      val apiKeysToClean = for ((k, ts) <- guardLastAccessTimeMap.asScala
                                if (currentTime - ts) > myResetGracePeriod.toMillis
      ) yield k

      apiKeysToClean.foreach(k =>
      {
        Future
        {
          guardActorRefMap.get(k) ? PoisonPill
        }
          .onComplete(
            _ =>
            {
              logger.debug("cleanActors - {} is killed in ActorSystem.", k: Any)
              guardActorRefMap.remove(k)
              guardLastAccessTimeMap.remove(k)
              logger.debug("cleanActors - {} is deleted in map.", k: Any)
            }
          )
      })

      logger.debug("cleanActors - After cleaning...")
      logger.debug("cleanActors - actors remained : " + guardActorRefMap.size())
    }
  }
}
