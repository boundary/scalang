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

import java.util.concurrent.BlockingQueue
import org.cliffc.high_scale_lib.NonBlockingHashMap

trait ReplyRegistry {
  val replyWaiters = new NonBlockingHashMap[(Pid,Reference),BlockingQueue[Any]]

  /**
   * Returns true if the reply delivery succeeded. False otherwise.
   */
  def tryDeliverReply(pid : Pid, msg : Any) = msg match {
    case (tag : Reference, reply : Any) =>
      val waiter = replyWaiters.remove((pid,tag))
      if (waiter == null) {
        false
      } else {
        waiter.offer(reply)
        true
      }
    case _ => false
  }
  
  def removeReplyQueue(pid : Pid, ref : Reference) {
    replyWaiters.remove((pid, ref))
  }
  
  def registerReplyQueue(pid : Pid, tag : Reference, queue : BlockingQueue[Any]) {
    replyWaiters.put((pid,tag), queue)
  }
}
