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
import netty.handler.codec.frame._

class EpmdDecoder extends FrameDecoder {
  override def decode(ctx : ChannelHandlerContext, channel : Channel, buffer : ChannelBuffer) : Object = {
    if (buffer.readableBytes < 1) return null
    val header = buffer.getByte(0)
    header match {
      case 121 => //decode alive2 resp
        if (buffer.readableBytes < 4) return null
        val result = buffer.getByte(1)
        val creation = buffer.getUnsignedShort(2)
        buffer.skipBytes(4)
        AliveResp(result, creation)
      case 119 => //decode port2 resp
        val result = buffer.getByte(1)
        if (result > 0) {
          buffer.skipBytes(2)
          PortPleaseError(result)
        } else {
          if (buffer.readableBytes < 12) return null
          val nlen = buffer.getUnsignedShort(10)
          if (buffer.readableBytes < (14 + nlen)) return null
          val elen = buffer.getUnsignedShort(12 + nlen)
          if (buffer.readableBytes < (14 + nlen + elen)) return null
          val portNo = buffer.getUnsignedShort(2)
          val bytes = new Array[Byte](nlen)
          buffer.getBytes(12, bytes)
          val nodeName = new String(bytes)
          buffer.skipBytes(14+nlen+elen)
          PortPleaseResp(portNo, nodeName)
        }
    }
  }
}
