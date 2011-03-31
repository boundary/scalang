package scalang.node

import scalang._

class NetKernel(pid : Pid) extends Process(pid : Pid) {
  
  override def handleMessage(msg : Any) = msg match {
    case (Symbol("$gen_call"), (pid : Pid, ref : Reference), ('is_auth, node : Symbol)) =>
      pid ! (ref, 'yes)
  }
}