package scalang.node

import org.jboss.{netty => netty}
import netty.handler.codec.oneone._
import netty.channel._
import netty.buffer._

class HandshakeEncoder extends OneToOneEncoder {
  
  def encode(ctx : ChannelHandlerContext, channel : Channel, obj : Any) : Object = {
    obj match {
      case NameMessage(version, flags, name) =>
        val bytes = name.getBytes
        val length = 7 + bytes.length
        val buffer = ChannelBuffers.dynamicBuffer(length+2)
        buffer.writeShort(length)
        buffer.writeByte(110)
        buffer.writeShort(version)
        buffer.writeInt(flags)
        buffer.writeBytes(bytes)
        buffer
      case StatusMessage(status) =>
        val bytes = status.getBytes
        val length = 1 + bytes.length
        val buffer = ChannelBuffers.dynamicBuffer(length+2)
        buffer.writeShort(length)
        buffer.writeByte(115)
        buffer.writeBytes(bytes)
        buffer
      case ChallengeMessage(version, flags, challenge, name) =>
        val bytes = name.getBytes
        val length = 11 + bytes.length
        val buffer = ChannelBuffers.dynamicBuffer(length+2)
        buffer.writeShort(length)
        buffer.writeByte(110)
        buffer.writeShort(version)
        buffer.writeInt(flags)
        buffer.writeInt(challenge)
        buffer.writeBytes(bytes)
        buffer
      case ChallengeReplyMessage(challenge, digest) =>
        val length = 5 + digest.length
        val buffer = ChannelBuffers.dynamicBuffer(length+2)
        buffer.writeShort(length)
        buffer.writeByte(114)
        buffer.writeInt(challenge)
        buffer.writeBytes(digest)
        buffer
      case ChallengeAckMessage(digest) =>
        val length = 1 + digest.length
        val buffer = ChannelBuffers.dynamicBuffer(length+2)
        buffer.writeShort(length)
        buffer.writeByte(97)
        buffer.writeBytes(digest)
        buffer
    }
  }
  
}