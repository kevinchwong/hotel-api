package com.kevinchwong.myagoda

import akka.actor.{Actor, Props}
import com.kevinchwong.myagoda.RateLimitGuardActor.{STATUS_AVAILABLE, STATUS_SUSPENDED}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

case class RateLimitGuardActor(val apiKey: String,
                               val rateLimitPeriod: Duration,
                               val suspendingPeriod: Duration) extends Actor {

  val logger = LoggerFactory.getLogger(classOf[RateLimitGuardActor]);

  // Internal statistic/status for the actor of each apiKey
  var lastSuccessAccess: Option[Long] = None
  var status: RateLimitGuardActor.Status = RateLimitGuardActor.STATUS_AVAILABLE

  // RateLimit Logic : send different Response back to sender, based on different situations...
  def receive = {
    case RateLimitGuardActor.Request =>
    {
      (lastSuccessAccess, rateLimitPeriod, status) match
      {
        case (_, x, _) if x.lt(0.millis) =>
        {
          logger.debug("Negative RateLimitPeriod - Don't pass it :{}", this: Any)
          val currentTime = System.currentTimeMillis
          sender ! RateLimitGuardActor.Response(apiKey, false, status, currentTime, lastSuccessAccess)
        }
        case (None, _, _) =>
        {
          logger.debug("Pass: new created actor : {}", this: Any)
          val currentTime = System.currentTimeMillis
          lastSuccessAccess = Some(System.currentTimeMillis)
          sender ! RateLimitGuardActor.Response(apiKey, true, status, currentTime, lastSuccessAccess)
        }
        case (Some(_), _, RateLimitGuardActor.STATUS_SUSPENDED) =>
        {
          val currentTime = System.currentTimeMillis
          if (currentTime > lastSuccessAccess.get + suspendingPeriod.toMillis)
          {
            logger.debug("Pass: SUSPENDED -> AVAILABLE : {}", this: Any)
            lastSuccessAccess = Some(currentTime)
            status = STATUS_AVAILABLE
            sender ! RateLimitGuardActor.Response(apiKey, true, status, currentTime, lastSuccessAccess)
          } else
          {
            logger.debug("Block: -> SUSPENDED -> SUSPENDED : {}", this: Any)
            sender ! RateLimitGuardActor.Response(apiKey, false, status, currentTime, lastSuccessAccess)
          }
        }
        case (Some(_), _, RateLimitGuardActor.STATUS_AVAILABLE) =>
        {
          val currentTime = System.currentTimeMillis
          if (currentTime > lastSuccessAccess.get + rateLimitPeriod.toMillis)
          {
            logger.debug("Pass: AVAILABLE -> AVAILABLE : {}", this: Any)
            lastSuccessAccess = Some(currentTime)
            sender ! RateLimitGuardActor.Response(apiKey, true, status, currentTime, lastSuccessAccess)
          } else
          {
            logger.debug("Block: AVAILABLE -> SUSPENDED : {}", this: Any)
            lastSuccessAccess = Some(currentTime)
            status = STATUS_SUSPENDED
            sender ! RateLimitGuardActor.Response(apiKey, false, status, currentTime, lastSuccessAccess)
          }
        }
      }
    }
    case _ =>
    {
      logger.warn("{} : Unknown Request: {}", this: Any, _: Any)
    }
  }
}

object RateLimitGuardActor {

  def props(apiKey: String, rateLimitPeriod: Duration, suspendingPeriod: Duration): Props = Props(
    new RateLimitGuardActor(apiKey, rateLimitPeriod, suspendingPeriod))

  sealed trait Status

  case object STATUS_AVAILABLE extends Status

  case object STATUS_SUSPENDED extends Status

  case class Request()

  case class Response(apiKey: String, pass: Boolean, status: Status, currentTime: Long, lastSuccessAccess: Option[Long])

}