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

import java.net._
import org.jboss.{netty => netty}
import netty.bootstrap._
import netty.channel._
import socket.nio._
import overlock.threadpool._
import java.util.concurrent.Callable

object Epmd {
  val defaultPort = 4369
  lazy val bossPool = ThreadPool.instrumentedFixed("scalang.epmd", "boss", 20)
  lazy val workerPool = ThreadPool.instrumentedFixed("scalang.epmd", "worker", 20)

  def apply(host : String) : Epmd = {
    val port = Option(System.getenv("ERL_EPMD_PORT")).map(_.toInt).getOrElse(defaultPort)
    new Epmd(host, port)
  }

  def apply(host : String, port : Int) : Epmd = {
    new Epmd(host, port)
  }
}

class Epmd(val host : String, val port : Int) {
  val bootstrap = new ClientBootstrap(
    new NioClientSocketChannelFactory(
      Epmd.bossPool,
      Epmd.workerPool))

  val handler = new EpmdHandler

  bootstrap.setPipelineFactory(new ChannelPipelineFactory {
    def getPipeline : ChannelPipeline = {
      Channels.pipeline(
        new EpmdEncoder,
        new EpmdDecoder,
        handler)
    }
  })

  val connectFuture = bootstrap.connect(new InetSocketAddress(host, port))
  val channel = connectFuture.awaitUninterruptibly.getChannel
  if(!connectFuture.isSuccess) {
    throw connectFuture.getCause
  }

  def close {
    channel.close
  }

  def alive(portNo : Int, nodeName : String) : Option[Int] = {
    var response: Callable[Any] = null
    handler.synchronized {
      response = handler.response
      channel.write(AliveReq(portNo,nodeName))
    }
    val aliveResponse = response.call.asInstanceOf[AliveResp]
    if (aliveResponse.result == 0) {
      Some(aliveResponse.creation)
    } else {
      sys.error("Epmd response was: " + aliveResponse.result)
      None
    }
  }

  def lookupPort(nodeName : String) : Option[Int] = {
    var response: Callable[Any] = null
    handler.synchronized {
      response = handler.response
      channel.write(PortPleaseReq(nodeName))
    }
    response.call match {
      case PortPleaseResp(portNo, _) => Some(portNo)
      case PortPleaseError(_) => None
    }
  }
}

