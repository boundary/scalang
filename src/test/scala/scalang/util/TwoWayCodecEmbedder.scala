package org.jboss.netty.handler.codec.embedder

import org.jboss.{netty => netty}
import netty.channel._
import netty.handler.codec.embedder._

class TwoWayCodecEmbedder[A](handlers : ChannelHandler*) extends AbstractCodecEmbedder[A](handlers : _*) {

  //derp
  def offer(input : Any) : Boolean = {
    true
  }

  def upstreamMessage(input : Any) {
    Channels.fireMessageReceived(getChannel, input)
  }

  def downstreamMessage(input : Any) {
    Channels.write(getChannel, input).setSuccess
  }
}
