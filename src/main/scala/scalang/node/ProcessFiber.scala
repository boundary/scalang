package scalang.node

import scalang._
import org.jetlang.channels._
import org.jetlang.core._
import org.jetlang.fibers._

class ProcessFiber(val process : Process, val fiber : Fiber, pid : Pid) {
  process.self = pid
  def deliverMessage(msg : Any) {
    msgChannel.publish(msg)
  }
  
  def deliverExit(from : Pid, msg : Any) {
    exitChannel.publish((from, msg))
  }
  
  val msgChannel = new MemoryChannel[Any]
  msgChannel.subscribe(fiber, new Callback[Any] {
    def onMessage(msg : Any) {
      try {
        process.delivery(msg)
      } catch {
        case e : Throwable =>
          
      }
    }
  })
  
  val exitChannel = new MemoryChannel[(Pid,Any)]
  exitChannel.subscribe(fiber, new Callback[(Pid,Any)] {
    def onMessage(msg : (Pid,Any)) {
      try {
        process.handleExit(msg._1, msg._2)
      } catch {
        case e : Throwable =>
          
      }
    }
  })
}