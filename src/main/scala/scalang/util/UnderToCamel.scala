package scalang.util

import java.lang.Character._
import scala.collection.mutable.StringBuilder

object UnderToCamel {
  implicit def underStringWrap(str : String) = new UnderToCamel(str)
}

class UnderToCamel(str : String) {
  def underToCamel : String = {
    val b = new StringBuilder
    var nextUpper = true
    for (char <- str) {
      if (char == '_') {
        nextUpper = true
      } else if (nextUpper) {
        b += toUpperCase(char)
        nextUpper = false
      } else {
        b += char
      }
    }
    b.toString
  }
}
