package scalang

import org.jetlang.fibers._
import org.cliffc.high_scale_lib._
import scalang.node.{ExitListener, SendListener}

abstract class Process {
  var self : Pid = null
  val links = new NonBlockingHashSet[Pid]
  
  implicit def pid2sendable(pid : Pid) = new PidSend(pid,this)
  implicit def sym2sendable(to : Symbol) = new SymSend(to,this)
  implicit def dest2sendable(dest : (Symbol,Symbol)) = new DestSend(dest,this)
  /**
   * Clients of this class must subclass the delivery method in order to
   * handle the delivery of any external erlang messages.
   */
  def delivery(msg : Any) : Unit
  
  def handleExit(from : Pid, reason : Any) {
    exit(reason)
  }
  
  def !(pid : Pid, msg : Any) : Any = {
    send(pid, msg)
  }
  
  def !(name : Symbol, msg : Any) : Any = {
    send(name, msg)
  }
  
  def !(dest : (Symbol,Symbol), msg : Any) : Any = {
    send(dest, msg)
  }
  
  def send(pid : Pid, msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(pid, msg)
    }
  }
  
  def send(name : Symbol, msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(name, msg)
    }
  }
  
  def send(dest : (Symbol,Symbol), msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(dest, msg)
    }
  }
  
  def link(to : Pid) {
    links.add(to)
  }
  
  def unlink(to : Pid) {
    links.remove(to)
  }
  
  @volatile var sendListeners : List[SendListener] = Nil
  @volatile var exitListeners : List[ExitListener] = Nil
  
  def addExitListener(listener : ExitListener) {
    exitListeners = listener :: exitListeners
  }
  
  def addSendListener(listener : SendListener) {
    sendListeners = listener :: sendListeners
  }
  
  def exit(reason : Any) {
    for (l <- exitListeners) {
      l.handleExit(self, reason)
    }
  }
}

class PidSend(to : Pid, proc : Process) {
  def !(msg : Any) {
    proc.send(to,msg)
  }
}

class SymSend(to : Symbol, proc : Process) {
  def !(msg : Any) {
    proc.send(to, msg)
  }
}

class DestSend(to : (Symbol,Symbol), proc : Process) {
  def !(msg : Any) {
    proc.send(to, msg)
  }
}