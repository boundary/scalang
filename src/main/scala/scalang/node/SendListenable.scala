package scalang.node

import scalang._

trait SendListenable {
  @volatile var sendListeners : List[SendListener] = Nil
  
  def addSendListener(listener : SendListener) {
    sendListeners = listener :: sendListeners
  }
  
  def notifySend(pid : Pid, msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(pid, msg)
    }
  }
  
  def notifySend(name : Symbol, msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(name, msg)
    }
  }
  
  def notifySend(dest : (Symbol,Symbol), msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(dest, msg)
    }
  }
  
}