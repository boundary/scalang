package scalang

import org.jetlang.fibers._
import org.cliffc.high_scale_lib._
import scalang.node.{ExitListener, SendListener, ProcessLike}

abstract class Process(val self : Pid) extends ProcessLike {
  implicit def pid2sendable(pid : Pid) = new PidSend(pid,this)
  implicit def sym2sendable(to : Symbol) = new SymSend(to,this)
  implicit def dest2sendable(dest : (Symbol,Symbol)) = new DestSend(dest,this)
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

class DestSend(to : (Symbol,Symbol), proc : Process) {
  def !(msg : Any) {
    proc.notifySend(to, msg)
  }
}