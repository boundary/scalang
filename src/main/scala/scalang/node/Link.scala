package scalang.node

import scalang._

case class Link(from : Pid, to : Pid) extends LinkListenable {  
  def break(reason : Any) {
    println("break on (" + this + ", " + reason + ")")
    notifyBreak(from, to, reason)
  }
}