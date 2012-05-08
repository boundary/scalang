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
    if (!buffer.readable) {
      return buffer
    }
    buffer.markReaderIndex
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
      case (_, _) => // overwhelmingly likely to be race between first message in and removal from pipeline
        buffer.resetReaderIndex
        buffer
    }
  }

}
