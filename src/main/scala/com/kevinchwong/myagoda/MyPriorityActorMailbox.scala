package com.kevinchwong.myagoda

import akka.actor.{ActorSystem, PoisonPill}
import akka.dispatch.{BoundedPriorityMailbox, PriorityGenerator}
import com.kevinchwong.myagoda.RateLimitGuardActor.Request
import com.typesafe.config.Config

import scala.concurrent.duration._

class MyPriorityActorMailbox(settings: ActorSystem.Settings, config: Config)
  extends BoundedPriorityMailbox(

    PriorityGenerator
    {
      case Request => 1
      case PoisonPill => 3
      case _ => 2
    },
    10,
    0.seconds
  ) {}