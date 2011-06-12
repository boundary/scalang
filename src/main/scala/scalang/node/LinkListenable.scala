package scalang.node

import scalang._

trait LinkListenable {
  @volatile var linkListeners : List[LinkListener] = Nil
  
  def addLinkListener(listener : LinkListener) {
    linkListeners = listener :: linkListeners
  }
  
  def notifyBreak(link : Link, reason : Any) {
    for (listener <- linkListeners) {
/*      println("notifying " + listener)*/
      listener.break(link, reason)
    }
  }
  
  def notifyDeliverLink(link : Link) {
    for (listener <- linkListeners) {
      listener.deliverLink(link)
    }
  }
}