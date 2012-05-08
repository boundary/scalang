package scalang

class FailProcess(ctx : ProcessContext) extends Process(ctx) {

  override def onMessage(msg : Any) {
    exit(msg)
  }

}
