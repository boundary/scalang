package scalang

import java.util.concurrent.BlockingQueue
import org.cliffc.high_scale_lib.NonBlockingHashMap

trait ReplyRegistry {
  val replyWaiters = new NonBlockingHashMap[(Pid,Reference),BlockingQueue[Any]]
  
  /**
   * Returns true if the reply delivery succeeded. False otherwise.
   */
  def tryDeliverReply(pid : Pid, msg : Any) = msg match {
    case (tag : Reference, reply : Any) =>
      val waiter = replyWaiters.remove((pid,tag))
      if (waiter == null) {
        false
      } else {
        waiter.offer(reply)
        true
      }
    case _ => false
  }
  
  def registerReplyQueue(pid : Pid, tag : Reference, queue : BlockingQueue[Any]) {
    replyWaiters.put((pid,tag), queue)
  }
}