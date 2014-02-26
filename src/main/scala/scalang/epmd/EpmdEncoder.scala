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

import org.jboss.{netty => netty}
import netty.buffer._
import netty.channel._
import netty.handler.codec.oneone._

object EpmdConst {
  val ntypeR6 = 110
  val ntypeR4Erlang = 109
  val ntypeR4Hidden = 104
}

class EpmdEncoder extends OneToOneEncoder {
  import EpmdConst._

  override def encode(ctx : ChannelHandlerContext, channel : Channel, msg : Object) : Object = {
    val bout = new ChannelBufferOutputStream(ChannelBuffers.dynamicBuffer(24, ctx.getChannel.getConfig.getBufferFactory))
    bout.writeShort(0) //length placeholder
    msg match {
      case AliveReq(portNo, nodeName) =>
        bout.writeByte(120)
        bout.writeShort(portNo)
        bout.writeByte(ntypeR6) //node type
        bout.writeByte(0) //protocol
        bout.writeShort(5) // highest version
        bout.writeShort(5) // lowest version
        bout.writeShort(nodeName.size) // name length
        bout.writeBytes(nodeName) // name
        bout.writeShort(0) //extra len
      case PortPleaseReq(nodeName) =>
        bout.writeByte(122)
        bout.writeBytes(nodeName)
    }
    val encoded = bout.buffer
    encoded.setShort(0, encoded.writerIndex - 2)
    encoded
  }
}
