package com.kevinchwong.myagoda

import java.net.{InetAddress, InetSocketAddress}

import com.sun.net.httpserver.HttpServer

// Main Http Server

// It works together with other two singletons in this system : RateLimitService, HotelDB, Settings and Tool

object MainHttpServer {

  Settings.loadConfig("config.json")

  val server = HttpServer.create(new InetSocketAddress(Settings.port), 0)
  server.createContext("/hotel/api/", new MainRateLimitedHandler)
  server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(Settings.numberOfThread))

  def main(args: Array[String]) {

    println
    println("MY HOTEL API -- (HttpServer with Rate Limit features)")
    println("Written by Kevin C. Wong")
    println("in October 2018")
    println
    println(" Configuration Information ")
    println(" ================================================================================")
    println(" Running on port                  : " + Settings.port)
    println(" Number of Threads                : " + Settings.numberOfThread)
    println(" Global Default Rate Limit Period : " + Settings.globalDefaultRateLimitPeriod)
    println(" Suspending Period                : " + Settings.suspendingPeriod)
    println(" Periodical Actors Cleaner        : " + (Settings.enableActorCleaner match
    {
      case true => "ON"
      case false => "OFF"
    }))
    println(" Cleaner in-between Period        : " + Settings.cleanerInbetweenPeriod)
    println(" Cleaner Grace Period             : " + Settings.cleanerGracePeriod)
    println("")
    val localhost: InetAddress = InetAddress.getLocalHost
    val localIpAddress: String = localhost.getHostAddress
    val port = Settings.port
    println("Server has been started. Now, you can send your URL post request to http://" + localIpAddress + ":" + port + "/hotel/api/")
    println("Press ctrl-C to exit")
    println("")
    server.start
    RateLimitService.startPeriodicalCleaner
    System.in.read
    terminate
  }

  def terminate = {
    RateLimitService.terminate
    server.stop(0)
  }
}

