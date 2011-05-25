package scalang.node

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.{netty => netty}
import scalang._
import netty.channel._
import netty.bootstrap._
import netty.handler.codec.frame._
import socket.nio.NioServerSocketChannelFactory

class ErlangNodeServer(node : ErlangNode, typeFactory : TypeFactory) {
  val bootstrap = new ServerBootstrap(
    new NioServerSocketChannelFactory(
      Executors.newCachedThreadPool,
      Executors.newCachedThreadPool))

  bootstrap.setPipelineFactory(new ChannelPipelineFactory {
    def getPipeline : ChannelPipeline = {
      val pipeline = Channels.pipeline

      pipeline.addLast("handshakeFramer", new LengthFieldBasedFrameDecoder(Short.MaxValue, 0, 2, 0, 2))
      pipeline.addLast("handshakeDecoder", new HandshakeDecoder)
      pipeline.addLast("handshakeEncoder", new HandshakeEncoder)
      pipeline.addLast("handshakeHandler", new ServerHandshakeHandler(node.name, node.cookie))
      pipeline.addLast("erlangFramer", new LengthFieldBasedFrameDecoder(Int.MaxValue, 0, 4, 0, 4))
      pipeline.addLast("encoderFramer", new LengthFieldPrepender(4))
      pipeline.addLast("erlangDecoder", new ScalaTermDecoder(typeFactory))
      pipeline.addLast("erlangEncoder", new ScalaTermEncoder)
      pipeline.addLast("erlangHandler", new ErlangHandler(node))

      pipeline
    }
  })
      
  val channel = bootstrap.bind(new InetSocketAddress(0))
  def port = channel.getLocalAddress.asInstanceOf[InetSocketAddress].getPort
}