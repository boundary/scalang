package scalang.node

import scalang._

class NetKernel(ctx : ProcessContext) extends Process(ctx : ProcessContext) {
  
  override def handleMessage(msg : Any) = msg match {
    case (Symbol("$gen_call"), (pid : Pid, ref : Reference), ('is_auth, node : Symbol)) =>
      println("mesg " + pid + ", " + ref + ", " + node)
      pid ! (ref, 'yes)
  }
}