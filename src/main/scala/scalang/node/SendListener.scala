package scalang.node

import scalang._

trait SendListener {
  def handleSend(to : Pid, msg : Any)
  def handleSend(to : Symbol, msg : Any)
  def handleSend(to : (Symbol,Symbol), from : Pid, msg : Any)
}