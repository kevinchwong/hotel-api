package com.kevinchwong.myagoda

/**
  * Created by kwong on 10/22/18.
  */

import akka.actor._
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.google.gson.Gson
import com.kevinchwong.myagoda.HotelDB.Hotel
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.concurrent.duration._
import scalaj.http.Http

class HotelDBTest
  extends TestKit(
      ActorSystem("TestKitUsageSpec", ConfigFactory.parseString(MainHttpServerTest.config))
    ) with DefaultTimeout
      with ImplicitSender
      with WordSpecLike
      with Matchers
      with BeforeAndAfterAll {

    override def beforeAll {
    }

    override def afterAll {
      shutdown()
    }

    implicit val ec = ExecutionContext.global
    val logger = LoggerFactory.getLogger(classOf[HotelDBTest]);


  "Test the HotelDB and csv files" should
      {
        // The purpose of this test is to verify the general functionality of HotelDB query:

        "HotelDB.query(\"1\") - expected pass" in
          {
            logger.info(HotelDB.query("1").toString)
            HotelDB.query("1").shouldBe(Hotel("Bangkok","1","Deluxe",1000.0))
          }

        "HotelDB.query(\"\") - expected NoSuchElementException" in
          {
            intercept[java.util.NoSuchElementException]
              {
                HotelDB.query("")
              }
          }

        "HotelDB.query(\"badKey\") - expected NoSuchElementException" in
          {
            intercept[java.util.NoSuchElementException]
              {
                HotelDB.query("badKey")
              }
          }

        "HotelDB.query(null) - expected NoSuchElementException" in
          {
            intercept[java.util.NoSuchElementException]
              {
                HotelDB.query(null)
              }
          }

        "HotelDB.queryJson(\"2\") - expected pass" in
          {
            logger.info((HotelDB.queryJson("2")))
            HotelDB.queryJson("2").shouldBe("{\n  \"city\": \"Amsterdam\",\n  \"cityId\": \"2\",\n  \"room\": \"Superior\",\n  \"price\": 2000.0\n}")
          }

        "HotelDB.queryJson(\"\")  - expected NoSuchElementException" in
          {
            intercept[java.util.NoSuchElementException]
              {
                HotelDB.queryJson("")
              }
          }

        "HotelDB.queryJson(\"badKey\")  - expected NoSuchElementException" in
          {
            intercept[java.util.NoSuchElementException]
              {
                HotelDB.queryJson("badKey")
              }
          }

        "HotelDB.queryJson(\"null\")  - expected NoSuchElementException" in
          {
            intercept[java.util.NoSuchElementException]
              {
                HotelDB.queryJson("null")
              }
          }

      }

  }

object HotelDBTest {

  // Define your test specific configuration here

  val config =

    """

    """
}