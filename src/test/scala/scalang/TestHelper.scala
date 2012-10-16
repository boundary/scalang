package scalang

import java.lang.ProcessBuilder
import java.lang.{Process => SysProcess}
import java.io._
import scala.collection.JavaConversions._
import scala.collection.mutable.StringBuilder

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

object Escript {
  def apply(command : String, args : String*) : SysProcess = {
    val url = getClass.getClassLoader.getResource(command)
    val file = new File(url.getFile)
    file.setExecutable(true)
    val builder = new ProcessBuilder(List(url.getFile) ++ args.toList)
    builder.start
  }
}

object EpmdCmd {
  def apply() : SysProcess = {
    val osName = System.getProperty("os.name").toLowerCase
    var builder : ProcessBuilder = null
    if (!osName.contains("win")) {
      builder = new ProcessBuilder("bash", "-c", "export PATH=" + formatPath + " && epmd")
    } else {
      builder = new ProcessBuilder("epmd")
    }
    builder.start
  }

  val additionalPaths = List("/usr/local/bin", "/usr/local/sbin")
  def formatPath: String = additionalPaths.mkString(":") + ":" + System.getenv("PATH")
}

object ReadLine {
  def apply(proc : SysProcess) : String = {
    val read = new BufferedReader(new InputStreamReader(proc.getInputStream))
    val line = read.readLine
    if(line == null) {
      throw new RuntimeException("error getting result from escript. ensure that erlang is installed and available on the path.")
    }
    line
  }
}
