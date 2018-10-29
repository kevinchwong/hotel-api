package com.kevinchwong.myagoda

import com.google.gson.internal.LinkedTreeMap

import scala.concurrent.duration._

// 'Settings' is a class with loadable mutable objects
// We can change the settings of the Http server any time.
// The reason is because we can have different configuration for different unit test cases without restart the server.

object Settings {

  // Gather data from config file
  var settings: LinkedTreeMap[String, Any] = new LinkedTreeMap

  var port: Int = 8000
  var numberOfThread: Int = 10

  var globalDefaultRateLimitPeriod: Duration = 10.seconds
  var rateLimitPeriodMap: LinkedTreeMap[String, String] = new LinkedTreeMap
  var suspendingPeriod: Duration = 5.minutes

  var actorTimeoutLimit: FiniteDuration = 1.hour
  var suspendDecisionTimeout: Duration = 30.seconds

  var enableActorCleaner: Boolean = true
  var cleanerGracePeriod: Duration = 1.hour
  var cleanerInbetweenPeriod: FiniteDuration = 24.hours


  def loadConfig(resourceFile: String) = {
    settings = Tool.gson.fromJson(scala.io.Source.fromResource(resourceFile).getLines.mkString, classOf[LinkedTreeMap[String, Any]])
      .get("settings").asInstanceOf[LinkedTreeMap[String, Any]]
    rateLimitPeriodMap = settings.get("rateLimitPeriodMap").asInstanceOf[LinkedTreeMap[String, String]]

    port = settings.get("port").asInstanceOf[Number].intValue
    numberOfThread = settings.get("numberOfThread").asInstanceOf[Number].intValue
    globalDefaultRateLimitPeriod = Duration(settings.get("globalDefaultRateLimitPeriod").toString)
    suspendingPeriod = Duration(settings.get("suspendingPeriod").toString)
    actorTimeoutLimit = Duration(settings.get("actorTimeoutLimit").toString).asInstanceOf[FiniteDuration]
    suspendDecisionTimeout = Duration(settings.get("suspendDecisionTimeout").toString)
    enableActorCleaner = settings.get("enableActorCleaner").asInstanceOf[Boolean]
    cleanerGracePeriod = Duration(settings.get("cleanerGracePeriod").toString)
    cleanerInbetweenPeriod = Duration(settings.get("cleanerInbetweenPeriod").toString).asInstanceOf[FiniteDuration]
  }

}
