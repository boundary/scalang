package scalang.node

import scalang._

trait LinkListener {
  def deliverLink(from : Pid, to : Pid)
  
  def break(from : Pid, to : Pid, reason : Any)
}