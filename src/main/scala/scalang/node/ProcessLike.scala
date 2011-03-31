package scalang.node

import scalang._
import org.cliffc.high_scale_lib.NonBlockingHashSet
import scala.collection.JavaConversions._

trait ProcessLike extends ExitListenable with SendListenable with LinkListenable {
  def self : Pid
  
  def handleMessage(msg : Any)
  
  def handleExit(from : Pid, reason : Any) {
    exit(reason)
  }
  
  def exit(reason : Any) {
    for (link <- links) {
      link.break(reason)
    }
    for(e <- exitListeners) {
      e.handleExit(self, reason)
    }
  }

  val links = new NonBlockingHashSet[Link]
  
  def link(to : Pid) {
    val l = Link(self, to)
    for (listener <- linkListeners) {
      l.addLinkListener(listener)
    }
    links.add(l)
  }
  
  def unlink(to : Pid) {
    links.remove(to)
  }
}