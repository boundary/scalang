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
package scalang.epmd

import java.util.concurrent.{ConcurrentLinkedQueue, Callable, CountDownLatch, atomic => atomic}
import atomic._
import org.jboss.{netty => netty}
import netty.channel._
import java.util.concurrent.TimeUnit
import com.boundary.logula.Logging

class EpmdHandler extends SimpleChannelUpstreamHandler with Logging {
  val queue = new ConcurrentLinkedQueue[EpmdResponse]

  def response : Callable[Any] = {
    val call = new EpmdResponse
    queue.add(call)
    call
  }

  override def channelClosed(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
    log.debug("Oh snap channel closed.")
  }

  override def channelDisconnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
    log.debug("Uh oh disconnect.")
  }

  override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent) {
    var rsp = queue.poll
    while (rsp != null) {
      rsp.setError(e.getCause)
      rsp = queue.poll
    }
  }

  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
    val response = e.getMessage
    val rsp = queue.poll()
    if (rsp != null) {
      rsp.set(response)
    }
    else {
      log.warn("Unable to find EpmdResponse for: %s", response)
    }
  }

  class EpmdResponse extends Callable[Any] {
    val response = new AtomicReference[Any]
    val error = new AtomicReference[Throwable]
    val lock = new CountDownLatch(1)

    def setError(t : Throwable) {
      error.set(t)
      lock.countDown()

    }

    def set(v : Any) {
      response.set(v)
      lock.countDown()
    }

    def call : Any = {
      if (lock.await(5000, TimeUnit.MILLISECONDS)) {
        if (error.get != null) {
          throw new Exception("EPMD Registration failed.", error.get)
        } else {
          response.get
        }
      } else {
        throw new Exception("EPMD Registration timed out.")
      }
    }
  }
}
