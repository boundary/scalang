package scalang

import java.util.concurrent.atomic._

object Node {
  def apply() {
    
  }
}

trait Node {
  def createProcess(process : Process) : Pid
  def createProcess(regName : String, process : Process) : Pid
  def registerName(regName : String, pid : Pid)
  def getNames : Seq[String]
  def whereis(name : String) : Option[Pid]
  def ping(node : String, timeout : Long)
  def nodes : Seq[String]
}

/*class ConcreteNode(name : String, host : String, cookie : String) extends Node {
  val creation = new AtomicInteger(0)
  
  
}*/