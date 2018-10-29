package com.kevinchwong.myagoda

import java.nio.charset.StandardCharsets

import com.google.gson.JsonObject
import com.kevinchwong.myagoda.RateLimitGuardActor.Response
import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import org.slf4j.LoggerFactory

import scala.io.Source.fromInputStream

class MainRateLimitedHandler extends HttpHandler {

  val logger = LoggerFactory.getLogger(classOf[MainRateLimitedHandler]);

  override def handle(exch: HttpExchange) {

    val outStream = exch.getResponseBody

    try
    {
      // Read json request
      val inStream = exch.getRequestBody
      val input = fromInputStream(inStream, "UTF8").mkString
      inStream.close

      // Logic to handle the request and using RateLimitService.allow(k) to make the decision
      val (res, resCode) = try
      {
        val jsonObj: JsonObject = Tool.gson.fromJson(input, classOf[JsonObject])
        logger.debug("read {}", jsonObj: Any)

        val apiKey = jsonObj.get("apiKey")
        apiKey match
        {
          case null =>
          {
            val tmpRes: JsonObject = new JsonObject
            tmpRes.addProperty("status", "No apiKey in input!")
            (tmpRes, 200)
          }
          case _ => RateLimitService.allow(apiKey.getAsString) match
          {
            case Response(_, false, _, _, _) =>
            {
              val tmpRes: JsonObject = new JsonObject
              tmpRes.addProperty("status", "Rate Limited Exceed!")
              (tmpRes, 429)
            }
            case Response(_, true, _, _, _) =>
            {
              val cityId = jsonObj.get("cityId").getAsString

              try
              {
                val hotel = HotelDB.query(cityId)
                val tmpRes: JsonObject = Tool.gson.toJsonTree(hotel).getAsJsonObject
                tmpRes.addProperty("status", "Ok!")
                (tmpRes, 200)
              } catch
              {
                case e: NoSuchElementException =>
                {
                  val tmpRes: JsonObject = new JsonObject
                  tmpRes.addProperty("status", "No Hotel found!")
                  (tmpRes, 200)
                }
              }
            }
          }

        }
      } catch
      {
        case e: com.google.gson.JsonSyntaxException =>
        {
          val tmpRes: JsonObject = new JsonObject
          tmpRes.addProperty("status", "Input is not in correct Json format!")
          (tmpRes, 200)
        }
        case e: com.google.gson.stream.MalformedJsonException =>
        {
          val tmpRes: JsonObject = new JsonObject
          tmpRes.addProperty("status", "Input is not in correct Json format!")
          (tmpRes, 200)
        }
        case e: Throwable =>
        {
          logger.error(e.toString)
          val tmpRes: JsonObject = new JsonObject
          tmpRes.addProperty("status", "Internal Error!")
          (tmpRes, 500)
        }
      }

      // Generate Output
      logger.debug("write {}", Tool.gson.toJson(res): Any)
      val resBytes = Tool.gson.toJson(res).getBytes(StandardCharsets.UTF_8.name)
      exch.sendResponseHeaders(resCode, resBytes.length)
      outStream.write(resBytes)
    }
    finally
    {
      outStream.close
    }

  }
}
