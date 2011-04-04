package scalang.node

import scalang._
import java.util.concurrent.TimeUnit
import concurrent.forkjoin.LinkedTransferQueue

class Mailbox(ctx : ProcessContext) extends ProcessLike {
  
  val referenceCounter = ctx.referenceCounter
  val self = ctx.pid
  
  val queue = new LinkedTransferQueue[Any]
  
  override def handleMessage(msg : Any) {
    queue.put(msg)
  }
  
  def receive : Any = {
    queue.take
  }
  
  def receive(timeout : Long) : Option[Any] = {
    Option(queue.poll(timeout, TimeUnit.MILLISECONDS))
  }
}