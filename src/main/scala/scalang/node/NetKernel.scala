package scalang.node

import scalang._

class NetKernel(ctx : ProcessContext) extends Process(ctx : ProcessContext) {
  
  override def onMessage(msg : Any) = msg match {
    case (Symbol("$gen_call"), (pid : Pid, ref : Reference), ('is_auth, node : Symbol)) =>
      pid ! (ref, 'yes)
  }
}