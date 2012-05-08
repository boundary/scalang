package scalang.epmd

import org.specs._
import scalang.util._
import org.jboss.{netty => netty}
import netty.handler.codec.embedder._
import netty.buffer._

class EpmdEncoderSpec extends SpecificationWithJUnit {
  "EpmdEncoder" should {
    "encode alive requests" in {
      val embedder = new EncoderEmbedder[ChannelBuffer](new EpmdEncoder)
      embedder.offer(AliveReq(5430, "fuck"))
      val buffer = embedder.poll
      val bytes = new Array[Byte](buffer.readableBytes)
      buffer.readBytes(bytes)
      bytes.deep must ==(ByteArray(0,17,120,21,54,110,0,0,5,0,5,0,4,102,117,99,107,0,0).deep)
    }

    "encode a port please request" in {
      val embedder = new EncoderEmbedder[ChannelBuffer](new EpmdEncoder)
      embedder.offer(PortPleaseReq("fuck"))
      val buffer = embedder.poll
      val bytes = new Array[Byte](buffer.readableBytes)
      buffer.readBytes(bytes)
      bytes.deep must ==(ByteArray(0,5,122,102,117,99,107).deep)
    }
  }
}
