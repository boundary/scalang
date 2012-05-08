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

trait Mailbox extends ProcessLike {
  def self : Pid
  def receive : Any
  def receive(timeout : Long) : Option[Any]
}

class MailboxProcess(ctx : ProcessContext) extends Mailbox {

  val referenceCounter = ctx.referenceCounter
  val self = ctx.pid

  val queue = new LinkedTransferQueue[Any]

  override def handleMessage(msg : Any) {
    queue.put(msg)
  }

  def receive : Any = {
    queue.take
  }

  def receive(timeout : Long) : Option[Any] = {
    Option(queue.poll(timeout, TimeUnit.MILLISECONDS))
  }
}
