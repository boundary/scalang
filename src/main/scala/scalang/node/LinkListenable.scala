package scalang.node

import scalang._

trait LinkListenable {
  @volatile var linkListeners : List[LinkListener] = Nil
  
  def addLinkListener(listener : LinkListener) {
    linkListeners = listener :: linkListeners
  }
  
  def notifyBreak(from : Pid, to : Pid, reason : Any) {
    for (listener <- linkListeners) {
      listener.break(from, to, reason)
    }
  }
  
  def notifyDeliverLink(from : Pid, to : Pid) {
    for (listener <- linkListeners) {
      listener.deliverLink(from, to)
    }
  }
}