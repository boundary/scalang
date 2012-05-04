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
      case buffer : ChannelBuffer => //we have yet to be removed from the conn
        buffer
    }
  }

}
