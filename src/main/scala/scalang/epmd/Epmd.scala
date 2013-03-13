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
import com.codahale.logula.Logging

case class EpmdConfig(
    host: String,
    port: Int,
    connectOnInit: Boolean = true,
    retries: Option[Int] = Some(Epmd.defaultRetries),
    retryInterval: Option[Double] = None,
    connectionTimeout: Option[Int] = None
)

object Epmd extends Logging {
  private val futurePollingInterval = 100 /* nanos */
  val defaultPort = 4369
  val defaultRetries = 5
  val defaultRetryInterval = 2.0 /*seconds*/

  lazy val bossPool = ThreadPool.instrumentedElastic("scalang.epmd", "boss", 1, 20)
  lazy val workerPool = ThreadPool.instrumentedElastic("scalang.epmd", "worker", 1, 20)

  def apply(host: String, port: Option[Int] = None): Epmd = {
    val epmdPort = port match {
      case Some(p) => p
      case None =>  Option(System.getenv("ERL_EPMD_PORT")).map(_.toInt).getOrElse(defaultPort)
    }
    Epmd(new EpmdConfig(host, epmdPort))
  }

  def apply(cfg: EpmdConfig): Epmd = {
    val epmd = new Epmd(cfg.host, cfg.port, cfg.connectionTimeout)
    if (cfg.connectOnInit) {
      connectWithRetries(epmd, cfg)
    }
    epmd
  }

  def connectWithRetries(epmd: Epmd, cfg: EpmdConfig) {
    var future = epmd.connect
    while (!future.isDone) {
      Thread.sleep(futurePollingInterval)
    }

    if (cfg.retries.isDefined) {
      val retries = cfg.retries.get

      val retryInterval = cfg.retryInterval.getOrElse(defaultRetryInterval)
      val retryIntervalMillis = (retryInterval * 1000.0).toInt

      var numRetries = 0
      while (!epmd.connected && numRetries < retries) {
        // Retry the connection
        if (!future.isSuccess) {
          log.warn("epmd connection failed. Retrying in %.1f seconds", retryInterval)
          Thread.sleep(retryIntervalMillis)
          future = epmd.connect
          numRetries += 1
        }
        // Poll the future
        while (!future.isDone) {
          Thread.sleep(futurePollingInterval)
        }
      }
    }
  }
}

class Epmd(val host : String, val port : Int, val defaultTimeout: Option[Int] = None) extends Logging {
  var channel: Channel = null
  val handler = new EpmdHandler

  val bootstrap = new ClientBootstrap(
    new NioClientSocketChannelFactory(
      Epmd.bossPool,
      Epmd.workerPool))

  bootstrap.setPipelineFactory(new ChannelPipelineFactory {
    def getPipeline : ChannelPipeline = {
      Channels.pipeline(
        new EpmdEncoder,
        new EpmdDecoder,
        handler)
    }
  })
  setTimeout(defaultTimeout)


  def setTimeout(timeout: Option[Int]) {
    if (timeout.isDefined) {
      bootstrap.setOption("connectTimeoutMillis", timeout.get * 1000)
    }
  }

  def connect: ChannelFuture = {
    val connectFuture = bootstrap.connect(new InetSocketAddress(host, port))
    connectFuture.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) {
        if (!future.isSuccess) {
          log.error(future.getCause, "Failed to connect to epmd on %s:%s", host, port)
        } else {
        channel = future.getChannel
        }
      }
    })
    connectFuture
  }

  def connectBlocking: Epmd = {
    val connectFuture = bootstrap.connect(new InetSocketAddress(host, port))
    channel = connectFuture.awaitUninterruptibly.getChannel
    this
  }

  def close {
    channel.close
  }

  def connected = (channel != null)

  def alive(portNo : Int, nodeName : String) : Option[Int] = {
    if (!connected) {
      log.error("'alive(%s, %s)' called before Epmd connected!", portNo, nodeName)
      return None
    }

    channel.write(AliveReq(portNo,nodeName))
    val response = handler.response.call.asInstanceOf[AliveResp]
    if (response.result == 0) {
      Some(response.creation)
    } else {
      log.error("Epmd response was: " + response.result)
      None
    }
  }

  def lookupPort(nodeName : String) : Option[Int] = {
    if(!connected) {
      log.error("'lookupPort(%s)' called before Epmd connected!", nodeName)
      return None
    }

    channel.write(PortPleaseReq(nodeName))
    handler.response.call match {
      case PortPleaseResp(portNo, _) => Some(portNo)
      case PortPleaseError(_) => None
    }
  }
}


