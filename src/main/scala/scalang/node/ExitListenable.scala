package scalang.node

import scalang._

trait ExitListenable {
  @volatile var exitListeners : List[ExitListener] = Nil
  
  def addExitListener(listener : ExitListener) {
    exitListeners = listener :: exitListeners
  }
  
  def notifyExit(from : Pid, reason : Any) {
    for (l <- exitListeners) {
      l.handleExit(from, reason)
    }
  }
}