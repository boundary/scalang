package scalang

class FailProcess(ctx : ProcessContext) extends Process(ctx) {
  
  override def handleMessage(msg : Any) {
    exit(msg)
  }
  
}