package scalang.node

import scalang._

trait ExitListener {
  def handleExit(from : Pid, reason : Any)
}