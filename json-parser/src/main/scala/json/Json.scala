package json

import scala.collection.mutable
import scala.util.Try

sealed trait Json

object Json {
  final case class Str(value: String) extends Json
  final case class Decim(value: BigDecimal) extends Json
  final case class Int(value: BigInt) extends Json
  final case class Bool(value: Boolean) extends Json
  final case class Obj(value: Map[String, Json]) extends Json

  object Obj {
    def empty: Obj = Obj(Map.empty)
  }
  final case class Arr(value: List[Json]) extends Json

  object Arr {
    def empty: Arr = Arr(Nil)
  }
  case object Null extends Json

  def parse(input: String): Json = {
    if (input == "null")
      Json.Null
    else if (Set("true", "false") contains input)
      Json.Bool(if (input == "true") true else false)
    else if (Try(BigDecimal(input)).isSuccess && input.contains("."))
      Json.Decim(BigDecimal(input))
    else if (Try(BigInt(input)).isSuccess)
      Json.Int(BigInt(input))
    else if (input.length >= 2 && input.startsWith("\"") && input.endsWith("\""))
      Json.Str(input.tail.init)
    else { // non primitives
      val map = mutable.LinkedHashMap.empty[String, Json]
      val normalized = input.substring(1, input.length - 1)
      val lines = normalized split "\n"
      var (insideJsObject, jsObjectEnd) = (false, false)
      var (jsonObjectStr, currentKey) = ("", "")
      var (openParens, closeParens) = (0, 0)
      var (insideJsArray, jsArrayEnd) = (false, false)

      for (line <- lines) {
        // null case
        if ((line.endsWith("null") || line.endsWith("null,")) && !insideJsObject) {
          val splitted = line.split(":")
          currentKey = splitted.head.trim.tail.init
          map += (currentKey -> Json.Null)
        }

        // json string case
        if ((line.endsWith("\",") || line.endsWith("\"")) && !insideJsObject) {
          val splitted = line.split(":")
          currentKey = splitted.head.trim.tail.init
          val value = splitted.last.trim.tail.dropRight(if (splitted.last.endsWith(",")) 2 else 1)
          map += (currentKey -> Json.Str(value))
        }

        // integer cases
        val firstIntCase = line.endsWith(",") && Try(BigInt(line.split(":").last.trim.init)).isSuccess
        val secondIntCase = Try(BigInt(line.split(":").last.trim)).isSuccess
        if (firstIntCase && !insideJsObject) {
          val splitted = line.split(":")
          currentKey = splitted.head.trim.tail.init
          val value = splitted.last.trim.init
          map += (currentKey -> Json.Int(BigInt(value)))
        }
        if (secondIntCase && !insideJsObject) {
          val splitted = line.split(":")
          currentKey = splitted.head.trim.tail.init
          val value = splitted.last.trim
          map += (currentKey -> Json.Int(BigInt(value)))
        }

        // bool case
        val isBool = Set("true", "false", "true,", "false,").exists(x => line.endsWith(x))
        if (isBool && !insideJsObject) {
          val splitted = line.split(":")
          currentKey = splitted.head.trim.tail.init
          val value = splitted.last
          map += (currentKey -> Json.Bool(if (value.contains("true")) true else false))
        }

        // decimal cases
        val firstDecCase =
          line.endsWith(",") && Try(BigDecimal(line.split(":").last.trim.init)).isSuccess && line.contains(".")
        val secondDecCase = Try(BigDecimal(line.split(":").last.trim)).isSuccess && line.contains(".")
        if (firstDecCase && !insideJsObject) {
          val splitted = line.split(":")
          currentKey = splitted.head.trim.tail.init
          val value = splitted.last.trim.init
          map += (currentKey -> Json.Decim(BigDecimal(value)))
        }
        if (secondDecCase && !insideJsObject) {
          val splitted = line.split(":")
          currentKey = splitted.head.trim.tail.init
          val value = splitted.last.trim
          map += (currentKey -> Json.Decim(BigDecimal(value)))
        }

        // js object cases
        if (line.endsWith("{")) {
          insideJsObject = true
          jsObjectEnd = false
          openParens += 1
          val splitted = line.split(":")
          if (openParens == 1) {
            currentKey = splitted.head.trim.tail.init
          }
        }
        if (line.endsWith("},") || line.endsWith("}")) {
          closeParens += 1
          jsObjectEnd = true

        }
        if (jsObjectEnd && (openParens == closeParens)) {
          insideJsObject = false
          jsonObjectStr += "\n" + line
          val lines = jsonObjectStr.split("\n")
          val json = "{\n" concat lines.drop(2).mkString("\n")
          map += currentKey -> parse(json)
        }
        if (insideJsObject) {
          jsonObjectStr += "\n" + line
        }

        if (line.endsWith("[")) {
          insideJsArray = true
          jsArrayEnd = false
        }

        if (line.endsWith("],") || line.endsWith("]")) {
          jsArrayEnd = true
        }

        if (jsArrayEnd) {
          insideJsArray = false
        }

        if (insideJsArray) {}

      }

      Json.Obj(map.toMap)
    }
  }
}