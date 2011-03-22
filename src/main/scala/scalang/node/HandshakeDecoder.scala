package scalang.node

import org.jboss.{netty => netty}
import netty.handler.codec.oneone._
import netty.channel._
import netty.buffer._

class HandshakeDecoder extends OneToOneDecoder {
  
  //we need to have a dirty fucking mode context
  //because name messages and challenge replies have
  //the same identifier
  @volatile var mode = 'name
  
  def decode(ctx : ChannelHandlerContext, channel : Channel, obj : Any) : Object = {
    //dispatch on first byte
    val buffer = obj.asInstanceOf[ChannelBuffer]
    (mode, buffer.readByte) match {
      case ('name, 110) => //name message
        val version = buffer.readShort
        val flags = buffer.readInt
        val nameLength = buffer.readableBytes
        val bytes = new Array[Byte](nameLength)
        buffer.readBytes(bytes)
        mode = 'challenge
        NameMessage(version, flags, new String(bytes))
      case ('challenge, 110) => //challenge message
        val version = buffer.readShort
        val flags = buffer.readInt
        val challenge = buffer.readInt
        val nameLength = buffer.readableBytes
        val bytes = new Array[Byte](nameLength)
        buffer.readBytes(bytes)
        ChallengeMessage(version, flags, challenge, new String(bytes))
      case (_, 115) => //status message
        val statusLength = buffer.readableBytes
        val bytes = new Array[Byte](statusLength)
        buffer.readBytes(bytes)
        StatusMessage(new String(bytes))
      case (_, 114) => //reply message
        val challenge = buffer.readInt
        val digestLength = buffer.readableBytes
        val bytes = new Array[Byte](digestLength)
        buffer.readBytes(bytes)
        ChallengeReplyMessage(challenge, bytes)
      case (_, 97) => //ack message
        val digestLength = buffer.readableBytes
        val bytes = new Array[Byte](digestLength)
        buffer.readBytes(bytes)
        ChallengeAckMessage(bytes)
    }
  }
  
}