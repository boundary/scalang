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

import scalang.node._
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

class FunProcess(fun : Mailbox => Unit, ctx : ProcessContext) extends ProcessAdapter {
  val queue = new LinkedBlockingQueue[Any]
  val referenceCounter = ctx.referenceCounter
  val self = ctx.pid
  val fiber = ctx.fiber
  val parentPid = self
  val parentRef = referenceCounter
  val parent = this
  val mbox = new Mailbox {
    def self = parentPid
    def referenceCounter = parentRef

    def handleMessage(msg : Any) {
      queue.offer(msg)
    }

    def receive : Any = {
      queue.take
    }

    def receive(timeout : Long) : Option[Any] = {
      Option(queue.poll(timeout, TimeUnit.MILLISECONDS))
    }
    
    def send(pid : Pid, msg : Any) = parent.notifySend(pid,msg)
    
    def send(name : Symbol, msg : Any) = parent.notifySend(name,msg)
    
    def send(dest : (Symbol,Symbol), from : Pid, msg : Any) = parent.notifySend(dest,from,msg)
    
    def exit(reason : Any) = parent.exit(reason)
    
    def link(to : Pid) = parent.link(to)
  }


  def start {
    fiber.execute(new Runnable {
      override def run {
        fun(mbox)
        exit('normal)
      }
    })
  }

  override def handleMessage(msg : Any) {
    queue.offer(msg)
  }

  override def handleExit(from : Pid, reason : Any) {
    queue.offer(('EXIT, from, reason))
  }
  
  override def handleMonitorExit(monitored : Any, ref : Reference, reason : Any) {
    queue.offer(('DOWN, ref, 'process, monitored, reason))
  }
  
  def cleanup = fiber.dispose
}
