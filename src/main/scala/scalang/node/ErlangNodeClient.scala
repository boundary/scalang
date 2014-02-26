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

import java.net.InetSocketAddress
import org.jboss.{netty => netty}
import scalang._
import netty.channel._
import netty.bootstrap._
import netty.handler.codec.frame._
import socket.nio.NioClientSocketChannelFactory
import com.boundary.logula.Logging


class ErlangNodeClient(
    node : ErlangNode,
    peer : Symbol,
    host : String,
    port : Int,
    control : Option[Any],
    typeFactory : TypeFactory,
    typeEncoder : TypeEncoder,
    typeDecoder : TypeDecoder,
    afterHandshake : Channel => Unit) extends Logging
{
  val bootstrap = new ClientBootstrap(
    new NioClientSocketChannelFactory(
      node.poolFactory.createBossPool,
      node.poolFactory.createWorkerPool))
  bootstrap.setPipelineFactory(new ChannelPipelineFactory {
    def getPipeline : ChannelPipeline = {
      val pipeline = Channels.pipeline

      val handshakeDecoder = new HandshakeDecoder
      handshakeDecoder.mode = 'challenge //first message on the client side is challenge, not name
      pipeline.addLast("executionHandler", node.executionHandler)
      pipeline.addLast("handshakeFramer", new LengthFieldBasedFrameDecoder(Short.MaxValue, 0, 2, 0, 2))
      pipeline.addLast("handshakeDecoder", handshakeDecoder)
      pipeline.addLast("handshakeEncoder", new HandshakeEncoder)
      pipeline.addLast("handshakeHandler", new ClientHandshakeHandler(node.name, node.cookie, node.posthandshake))
      pipeline.addLast("erlangFramer", new LengthFieldBasedFrameDecoder(Int.MaxValue, 0, 4, 0, 4))
      pipeline.addLast("encoderFramer", new LengthFieldPrepender(4))
      pipeline.addLast("erlangDecoder", new ScalaTermDecoder(peer, typeFactory, typeDecoder))
      pipeline.addLast("erlangEncoder", new ScalaTermEncoder(peer, typeEncoder))
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
        node.disconnected(peer, channel)
      }
    }
  })
}
