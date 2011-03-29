package scalang

import java.lang.ProcessBuilder
import java.lang.{Process => SysProcess}
import scala.collection.JavaConversions._

object ErlangVM {
  def apply(name : String, cookie : String, eval : Option[String]) : SysProcess = {
    val commands = List("erl", "-sname", name, "-setcookie", cookie, "-noshell", "-smp") ++
      (for (ev <- eval) yield {
        List("-eval", ev)
      }).getOrElse(Nil)
    val builder = new ProcessBuilder(commands)
    builder.start
  }
}

object EpmdCmd {
  def apply() : SysProcess = {
    val builder = new ProcessBuilder("epmd")
    builder.start
  }
}