package scalang

import scalang.node._
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

class FunProcess(fun : Mailbox => Unit, ctx : ProcessContext) extends Process(ctx) {
  val queue = new LinkedBlockingQueue[Any]
  val parentPid = self
  val parentRef = referenceCounter
  
  val mbox = new Mailbox {
    def self = parentPid
    def referenceCounter = parentRef
    
    override def handleMessage(msg : Any) {
      queue.offer(msg)
    }
    
    def receive : Any = {
      queue.take
    }
    
    def receive(timeout : Long) : Option[Any] = {
      Option(queue.poll(timeout, TimeUnit.MILLISECONDS))
    }
  }
  
  
  def start {
    fiber.execute(new Runnable {
      override def run {
        fun(mbox)
        exit('normal)
      }
    })
  }
  
  override def onMessage(msg : Any) {
    queue.offer(msg)
  }
  
}