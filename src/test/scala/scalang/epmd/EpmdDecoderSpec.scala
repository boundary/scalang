package scalang.epmd

import org.specs._
import scalang.util._
import org.jboss.{netty => netty}
import netty.handler.codec.embedder._
import netty.buffer.ChannelBuffers._
import EpmdConst._

class EpmdDecoderSpec extends SpecificationWithJUnit {
  "EpmdDecoder" should {
    "decode alive responses" in {
      val embedder = new DecoderEmbedder[AliveResp](new EpmdDecoder)
      val buffer = copiedBuffer(ByteArray(121, 0, 0, 20))
      embedder.offer(buffer)
      val resp = embedder.poll
      resp must ==(AliveResp(0,20))
    }

    "decode port please response" in {
      val embedder = new DecoderEmbedder[PortPleaseResp](new EpmdDecoder)
      val buffer = copiedBuffer(ByteArray(119, 0, 20, 140, ntypeR6, 0, 0, 5, 0, 5, 0, 4, 102, 117, 99, 107, 0, 0))
      embedder.offer(buffer)
      val resp = embedder.poll
      resp must ==(PortPleaseResp(5260, "fuck"))
    }

    "decode port please error" in {
      val embedder = new DecoderEmbedder[PortPleaseError](new EpmdDecoder)
      val buffer = copiedBuffer(ByteArray(119, 1))
      embedder.offer(buffer)
      val resp = embedder.poll
      resp must ==(PortPleaseError(1))
    }
  }
}
