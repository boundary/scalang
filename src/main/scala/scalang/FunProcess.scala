package scalang

class FunProcess(fun : => Unit, ctx : ProcessContext) extends Process(ctx) {
  
  def start {
    fiber.execute(new Runnable {
      override def run {
        fun
        exit('normal)
      }
    })
  }
  
  override def onMessage(msg : Any) {
    
  }
  
}