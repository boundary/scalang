package scalang.node

import scalang._

/**
 Exit notifications are intended for internal book-keeping tasks.  They are not meant for link breakages,
 which require the pid of both ends.
*/

trait ExitListener {
  def handleExit(from : Pid, reason : Any)
}