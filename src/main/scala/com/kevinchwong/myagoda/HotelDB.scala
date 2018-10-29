package com.kevinchwong.myagoda

import scala.io.Source

object HotelDB {

  case class Hotel(city: String, cityId: String, room: String, price: Double)

  val db: Map[String, Hotel] = loadcsv("hoteldb.csv")

  def loadcsv(filename: String): Map[String, Hotel] = {
    Source.fromResource(filename).getLines
      .zipWithIndex
      .filter(tuple => tuple._2 != 0)
      .map(
        tuple =>
        {
          val ss = tuple._1.split(",")
          (ss(1) -> Hotel(ss(0), ss(1), ss(2), ss(3).toDouble))
        }
      ).toMap
  }

  def query(cityId: String): Hotel = {
    db(cityId)
  }

  def queryJson(cityId: String): String = {
    Tool.gson.toJson(db(cityId))
  }

}
