package scalang.node

import scalang._
import org.jetlang.channels._
import org.jetlang.core._
import org.jetlang.fibers._

class ProcessFiber(val process : ProcessLike, val fiber : Fiber) extends ProcessLike {
  
  override def self = process.self
  
  override def referenceCounter = process.referenceCounter
  
  override def handleMessage(msg : Any) {
    msgChannel.publish(msg)
  }
  
  override def handleExit(from : Pid, msg : Any) {
    exitChannel.publish((from, msg))
  }
  
  override def link(to : Pid) = process.link(to)
  
  override def unlink(to : Pid) = process.unlink(to)
  
  override def addExitListener(listener : ExitListener) = process.addExitListener(listener)
  
  override def notifyExit(from : Pid, reason : Any) = process.notifyExit(from, reason)
  
  override def addSendListener(listener : SendListener) = process.addSendListener(listener)
  
  override def notifySend(pid : Pid, msg : Any) = process.notifySend(pid, msg)
  
  override def notifySend(name : Symbol, msg : Any) = process.notifySend(name, msg)
  
  override def notifySend(dest : (Symbol,Symbol), from : Pid, msg : Any) = process.notifySend(dest, from, msg)
  
  override def addLinkListener(listener : LinkListener) = process.addLinkListener(listener)
  
  override def notifyBreak(from : Pid, to : Pid, reason : Any) = process.notifyBreak(from, to, reason)
  
  val msgChannel = new MemoryChannel[Any]
  msgChannel.subscribe(fiber, new Callback[Any] {
    def onMessage(msg : Any) {
      try {
        process.handleMessage(msg)
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