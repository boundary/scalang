package scalang.util

import java.lang.Character._
import scala.collection.mutable.StringBuilder

object CamelToUnder {
  implicit def stringWrap(str : String) = new CamelToUnder(str)
}

class CamelToUnder(str : String) {
  def camelToUnderscore : String = {
    val b = new StringBuilder
    for (char <- str) {
      if (isUpperCase(char) && b.size == 0) {
        b += toLowerCase(char)
      } else if (isUpperCase(char)) {
        b ++= "_"
        b += toLowerCase(char)
      } else {
        b += char
      }
    }
    b.toString
  }
}
