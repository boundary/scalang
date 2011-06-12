package scalang.node

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.{netty => netty}
import scalang._
import netty.channel._
import netty.bootstrap._
import netty.handler.codec.frame._
import netty.handler.timeout._
import netty.util.HashedWheelTimer
import socket.nio.NioClientSocketChannelFactory

class ErlangNodeClient(
    node : ErlangNode,
    host : String,
    port : Int,
    control : Option[Any], 
    typeFactory : TypeFactory, 
    afterHandshake : Channel => Unit) {
  val bootstrap = new ClientBootstrap(
    new NioClientSocketChannelFactory(
      node.poolFactory.createBossPool,
      node.poolFactory.createWorkerPool))
  bootstrap.setPipelineFactory(new ChannelPipelineFactory {
    def getPipeline : ChannelPipeline = {
      val pipeline = Channels.pipeline
      
      val handshakeDecoder = new HandshakeDecoder
      handshakeDecoder.mode = 'challenge //first message on the client side is challenge, not name
      
      pipeline.addLast("handshakeFramer", new LengthFieldBasedFrameDecoder(Short.MaxValue, 0, 2, 0, 2))
      pipeline.addLast("handshakeDecoder", handshakeDecoder)
      pipeline.addLast("handshakeEncoder", new HandshakeEncoder)
      pipeline.addLast("handshakeHandler", new ClientHandshakeHandler(node.name, node.cookie))
      pipeline.addLast("erlangFramer", new LengthFieldBasedFrameDecoder(Int.MaxValue, 0, 4, 0, 4))
      pipeline.addLast("encoderFramer", new LengthFieldPrepender(4))
      pipeline.addLast("timeout", new ReadTimeoutHandler(node.timer, 60))
      pipeline.addLast("erlangDecoder", new ScalaTermDecoder(typeFactory))
      pipeline.addLast("erlangEncoder", new ScalaTermEncoder)
      pipeline.addLast("erlangHandler", new ErlangHandler(node, afterHandshake))
      
      pipeline
    }
  })
  
  val future = bootstrap.connect(new InetSocketAddress(host, port))
  val channel = future.getChannel
  future.addListener(new ChannelFutureListener {
    def operationComplete(f : ChannelFuture) {
      if (f.isSuccess) {
        for (c <- control) {
          channel.write(c)
        }
      } else {
        f.getCause.printStackTrace
      }
    }
  })
}