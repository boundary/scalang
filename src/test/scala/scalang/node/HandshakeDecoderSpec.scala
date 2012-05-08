package scalang.node

import org.specs._
import org.specs.runner._
import scalang.util._
import org.jboss.{netty => netty}
import netty.handler.codec.embedder._
import netty.buffer.ChannelBuffers._

class HandshakeDecoderSpec extends SpecificationWithJUnit {
  "HandshakeDecoder" should {
    "decode name messages" in {
      val decoder = new HandshakeDecoder
      val embedder = new DecoderEmbedder[NameMessage](decoder)

      val bytes = ByteArray(110, 0,5, 0,0,127,253, 116,109,112,64,98,108,97,104)
      val buffer = copiedBuffer(bytes)
      embedder.offer(buffer)
      val msg = embedder.poll
      msg must ==(NameMessage(5, 32765, "tmp@blah"))

      decoder.mode must ==('challenge) //decoding a name message should trigger a state change
    }

    "decode status messages" in {
      val decoder = new HandshakeDecoder
      val embedder = new DecoderEmbedder[StatusMessage](decoder)

      val bytes = ByteArray(115, 111,107)
      val buffer = copiedBuffer(bytes)
      embedder.offer(buffer)
      val msg = embedder.poll
      msg must ==(StatusMessage("ok"))
    }

    "decode challenge messages" in {
      val decoder = new HandshakeDecoder
      val embedder = new DecoderEmbedder[ChallengeMessage](decoder)
      decoder.mode = 'challenge
      val bytes = ByteArray(110, 0,5, 0,0,127,253, 0,1,56,213, 116,109,112,64,98,108,97,104)
      val buffer = copiedBuffer(bytes)
      embedder.offer(buffer)
      val msg = embedder.poll
      msg must ==(ChallengeMessage(5, 32765, 80085, "tmp@blah"))
    }

    "decode reply messages" in {
      val decoder = new HandshakeDecoder
      val embedder = new DecoderEmbedder[ChallengeReplyMessage](decoder)
      val bytes = ByteArray(114, 0,1,56,213, 112,111,111,111,111,111,111,111,111,111,111,111,111,111,111,112)
      val buffer = copiedBuffer(bytes)
      embedder.offer(buffer)
      val msg = embedder.poll
      msg must beLike { case ChallengeReplyMessage(80085, digest) =>
        digest.deep == ByteArray(112,111,111,111,111,111,111,111,111,111,111,111,111,111,111,112).deep
      }
    }

    "decode ack messages" in {
      val decoder = new HandshakeDecoder
      val embedder = new DecoderEmbedder[ChallengeAckMessage](decoder)
      val bytes = ByteArray(97, 112,111,111,111,111,111,111,111,111,111,111,111,111,111,111,112)
      val buffer = copiedBuffer(bytes)
      embedder.offer(buffer)
      val msg = embedder.poll
      msg must beLike { case ChallengeAckMessage(digest) =>
        digest.deep == ByteArray(112,111,111,111,111,111,111,111,111,111,111,111,111,111,111,112).deep
      }
    }
  }
}
