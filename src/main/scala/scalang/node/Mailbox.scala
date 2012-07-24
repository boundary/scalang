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
package scalang.node

import scalang._
import java.util.concurrent.TimeUnit
import concurrent.forkjoin.LinkedTransferQueue

trait Mailbox {
  def self : Pid
  def receive : Any
  def receive(timeout : Long) : Option[Any]
  def send(pid : Pid, msg : Any) : Any
  def send(name : Symbol, msg : Any) : Any
  def send(dest : (Symbol, Symbol), from : Pid, msg : Any) : Any
  def exit(reason : Any)
  def link(to : Pid)
}

class MailboxProcess(ctx : ProcessContext) extends ProcessAdapter with Mailbox {
  val self = ctx.pid
  val fiber = ctx.fiber
  val referenceCounter = ctx.referenceCounter
  
  val queue = new LinkedTransferQueue[Any]
  def cleanup {}
  val adapter = ctx.adapter

  def receive : Any = {
    queue.take
  }

  def receive(timeout : Long) : Option[Any] = {
    Option(queue.poll(timeout, TimeUnit.MILLISECONDS))
  }
  
  override def handleMessage(msg : Any) {
    queue.offer(msg)
  }

  override def handleExit(from : Pid, reason : Any) {
    exit(reason)
  }
  
  override def handleMonitorExit(monitored : Any, ref : Reference, reason : Any) {
    queue.offer(('DOWN, ref, 'process, monitored, reason))
  }
  
  def send(to : Pid, msg : Any) = notifySend(to, msg)
  def send(dest : (Symbol, Symbol), from : Pid, msg : Any) = notifySend(dest, from, msg)
  def send(name : Symbol, msg : Any) = notifySend(name, msg)
}
