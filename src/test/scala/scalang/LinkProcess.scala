package scalang

class LinkProcess(ctx : ProcessContext) extends Process(ctx) {

  override def onMessage(msg : Any) = msg match {
    case (linkTo : Pid, sendTo : Pid) =>
      link(linkTo)
      sendTo ! 'ok
  }

}
