package scalang

class EchoProcess(ctx : ProcessContext) extends Process(ctx) {

  override def onMessage(msg : Any) = msg match {
    case (from : Pid, echo : Any) =>
      from ! echo
  }
}
