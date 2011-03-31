package scalang.node

import scalang._

trait LinkListener {
  def break(from : Pid, to : Pid, reason : Any)
}