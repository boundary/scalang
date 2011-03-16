package scalang.epmd

import java.net._
import java.util.concurrent.Executors
import org.jboss.{netty => netty}
import netty.bootstrap._
import netty.channel._
import socket.nio._
import netty.handler.codec.frame._

object Epmd {
  val defaultPort = 4369
  
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
      Executors.newCachedThreadPool,
      Executors.newCachedThreadPool))
      
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
    bootstrap.releaseExternalResources
    throw connectFuture.getCause
  }
  
  def close {
    channel.close
    bootstrap.releaseExternalResources
  }
  
  override def finalize {
    close
  }
  
  def alive(portNo : Int, nodeName : String) : Option[Int] = {
    channel.write(AliveReq(portNo,nodeName))
    val response = handler.response.call.asInstanceOf[AliveResp]
    if (response.result == 0) {
      Some(response.creation)
    } else {
      None
    }
  }
  
  def lookupPort(nodeName : String) : Option[Int] = {
    channel.write(PortPleaseReq(nodeName))
    handler.response.call match {
      case PortPleaseResp(portNo, _) => Some(portNo)
      case PortPleaseError(_) => None
    }
  }
}

