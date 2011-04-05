package scalang

class EchoProcess(ctx : ProcessContext) extends Process(ctx) {
  
  def handleMessage(msg : Any) = msg match {
    case (from : Pid, echo : Any) =>
      from ! echo
  }
}