package scalang.epmd

import java.net._
import java.util.concurrent.Executors
import org.jboss.{netty => netty}
import netty.bootstrap._
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
        val creation = buffer.getShort(2)
        buffer.skipBytes(4)
        AliveResp(result, creation)
      case 119 => //decode port2 resp
        val result = buffer.getByte(1)
        if (result > 0) {
          buffer.skipBytes(2)
          PortPleaseError(result)
        } else {
          if (buffer.readableBytes < 12) return null
          val nlen = buffer.getShort(10)
          if (buffer.readableBytes < (14 + nlen)) return null
          val elen = buffer.getShort(12 + nlen)
          if (buffer.readableBytes < (14 + nlen + elen)) return null
          val portNo = buffer.getShort(2)
          val bytes = new Array[Byte](nlen)
          buffer.getBytes(12, bytes)
          val nodeName = new String(bytes)
          buffer.skipBytes(14+nlen+elen)
          PortPleaseResp(portNo, nodeName)
        }
    }
  }
}