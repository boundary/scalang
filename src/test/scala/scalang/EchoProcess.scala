package scalang

class EchoProcess(ctx : ProcessContext) extends Process(ctx) {
  
  def handleMessage(msg : Any) = msg match {
    case (from : Pid, echo : Any) =>
      println("got " + echo)
      from ! echo
  }
}