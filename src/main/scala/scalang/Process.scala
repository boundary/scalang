package scalang

import org.jetlang.fibers._
import org.cliffc.high_scale_lib._
import scalang.node.{ExitListener, SendListener, ProcessLike}
import org.jetlang.channels._
import org.jetlang.core._
import scalang.util.Log

abstract class Process(ctx : ProcessContext) extends ProcessLike with Log {
  val self = ctx.pid
  val fiber = ctx.fiber
  val referenceCounter = ctx.referenceCounter
  val replyRegistry = ctx.replyRegistry
  
  implicit def pid2sendable(pid : Pid) = new PidSend(pid,this)
  implicit def sym2sendable(to : Symbol) = new SymSend(to,this)
  implicit def dest2sendable(dest : (Symbol,Symbol)) = new DestSend(dest,self,this)
  
  /**
   * Subclasses should override this method with their own message handlers
   */
  def onMessage(msg : Any)
  
  /**
   * Subclasses wishing to trap exits should override this method.
   */
  def trapExit(from : Pid, msg : Any) {
    exit(msg)
  }
  
  override def handleMessage(msg : Any) {
    msgChannel.publish(msg)
  }
  
  override def handleExit(from : Pid, msg : Any) {
    exitChannel.publish((from,msg))
  }
  
  val p = this
  val msgChannel = new MemoryChannel[Any]
  msgChannel.subscribe(ctx.fiber, new Callback[Any] {
    def onMessage(msg : Any) {
      try {
        p.onMessage(msg)
      } catch {
        case e : Throwable =>
          error("An error occurred in actor " + this, e)
          exit(e.getMessage)
      }
    }
  })
  
  val exitChannel = new MemoryChannel[(Pid,Any)]
  exitChannel.subscribe(ctx.fiber, new Callback[(Pid,Any)] {
    def onMessage(msg : (Pid,Any)) {
      try {
        trapExit(msg._1, msg._2)
      } catch {
        case e : Throwable =>
          error("An error occurred during handleExit in actor " + this, e)
          exit(e.getMessage)
      }
    }
  })
}

class PidSend(to : Pid, proc : Process) {
  def !(msg : Any) {
    proc.notifySend(to,msg)
  }
}

class SymSend(to : Symbol, proc : Process) {
  def !(msg : Any) {
    proc.notifySend(to, msg)
  }
}

class DestSend(to : (Symbol,Symbol), from : Pid, proc : Process) {
  def !(msg : Any) {
    proc.notifySend(to, from, msg)
  }
}