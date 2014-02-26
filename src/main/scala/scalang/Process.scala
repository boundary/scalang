//
// Copyright 2011, Boundary
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package scalang

import scalang.node.ProcessLike
import scala._
import com.boundary.logula.Logging

abstract class Process(ctx : ProcessContext) extends ProcessLike with Logging {
  val self = ctx.pid 
  val adapter = ctx.adapter
  val referenceCounter = ctx.referenceCounter
  val replyRegistry = ctx.replyRegistry
  val node = ctx.node

  implicit def pid2sendable(pid : Pid) = new PidSend(pid,this)
  implicit def sym2sendable(to : Symbol) = new SymSend(to,this)
  implicit def dest2sendable(dest : (Symbol,Symbol)) = new DestSend(dest,self,this)

  def sendEvery(pid : Pid, msg : Any, delay : Long) = adapter.sendEvery(pid, msg, delay)
  def sendEvery(name : Symbol, msg : Any, delay : Long) = adapter.sendEvery(name, msg, delay)
  def sendEvery(name : (Symbol,Symbol), msg : Any, delay : Long) = adapter.sendEvery(name, msg, delay)

  def sendAfter(pid : Pid, msg : Any, delay : Long) = adapter.sendAfter(pid, msg, delay)
  def sendAfter(name : Symbol, msg : Any, delay : Long) = adapter.sendAfter(name, msg, delay)
  def sendAfter(dest : (Symbol,Symbol), msg : Any, delay : Long) = adapter.sendAfter(dest, msg, delay)

  override def handleMessage(msg : Any) {
    onMessage(msg)
  }

  override def handleExit(from : Pid, msg : Any) {
    trapExit(from, msg)
  }

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

  def handleMonitorExit(monitored : Any, ref : Reference, reason : Any) {
    trapMonitorExit(monitored, ref, reason)
  }

  /**
   * Subclasses wishing to trap monitor exits should override this method.
   */
  def trapMonitorExit(monitored : Any, ref : Reference, reason : Any) {
  }

}

class PidSend(to : Pid, proc : Process) {
  def !(msg : Any) {
    proc.adapter.notifySend(to,msg)
  }
}

class SymSend(to : Symbol, proc : Process) {
  def !(msg : Any) {
    proc.adapter.notifySend(to, msg)
  }
}

class DestSend(to : (Symbol,Symbol), from : Pid, proc : Process) {
  def !(msg : Any) {
    proc.adapter.notifySend(to, from, msg)
  }
}
