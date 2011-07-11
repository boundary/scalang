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

class FunProcess(fun : Mailbox => Unit, ctx : ProcessContext) extends Process(ctx) {
  val queue = new LinkedBlockingQueue[Any]
  val parentPid = self
  val parentRef = referenceCounter
  
  val mbox = new Mailbox {
    def self = parentPid
    def referenceCounter = parentRef
    
    override def handleMessage(msg : Any) {
      queue.offer(msg)
    }
    
    def receive : Any = {
      queue.take
    }
    
    def receive(timeout : Long) : Option[Any] = {
      Option(queue.poll(timeout, TimeUnit.MILLISECONDS))
    }
  }
  
  
  def start {
    fiber.execute(new Runnable {
      override def run {
        fun(mbox)
        exit('normal)
      }
    })
  }
  
  override def onMessage(msg : Any) {
    queue.offer(msg)
  }
  
}