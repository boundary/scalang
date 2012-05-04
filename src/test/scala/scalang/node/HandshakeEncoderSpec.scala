package scalang.node

import org.specs._
import org.specs.runner._
import scalang.util._
import org.jboss.{netty => netty}
import netty.handler.codec.embedder._
import netty.buffer._
import netty.buffer.ChannelBuffers._

class HandshakeEncoderSpec extends SpecificationWithJUnit {
  "HandshakeEncoder" should {
    "encode name messages" in {
      val encoder = new HandshakeEncoder
      val embedder = new EncoderEmbedder[ChannelBuffer](encoder)
      embedder.offer(NameMessage(5, 32765, "tmp@blah"))

      val buffer = embedder.poll
      val bytes = buffer.array
      bytes.deep must ==(ByteArray(0,15, 110, 0,5, 0,0,127,253, 116,109,112,64,98,108,97,104).deep)
    }

    "encode status messages" in {
      val encoder = new HandshakeEncoder
      val embedder = new EncoderEmbedder[ChannelBuffer](encoder)
      embedder.offer(StatusMessage("ok"))

      val buffer = embedder.poll
      val bytes = buffer.array
      bytes.deep must ==(ByteArray(0,3, 115, 111,107).deep)
    }

    "encode challenge messages" in {
      val encoder = new HandshakeEncoder
      val embedder = new EncoderEmbedder[ChannelBuffer](encoder)
      embedder.offer(ChallengeMessage(5, 32765, 80085, "tmp@blah"))

      val buffer = embedder.poll
      val bytes = buffer.array
      bytes.deep must ==(ByteArray(0,19, 110, 0,5, 0,0,127,253, 0,1,56,213, 116,109,112,64,98,108,97,104).deep)
    }

    "encode challenge reply messages" in {
      val encoder = new HandshakeEncoder
      val embedder = new EncoderEmbedder[ChannelBuffer](encoder)
      embedder.offer(ChallengeReplyMessage(80085, ByteArray(112,111,111,111,111,111,111,111,111,111,111,111,111,111,111,112)))

      val buffer = embedder.poll
      val bytes = buffer.array
      bytes.deep must ==(ByteArray(0,21, 114, 0,1,56,213, 112,111,111,111,111,111,111,111,111,111,111,111,111,111,111,112).deep)
    }

    "encode ack messages" in {
      val encoder = new HandshakeEncoder
      val embedder = new EncoderEmbedder[ChannelBuffer](encoder)
      embedder.offer(ChallengeAckMessage(ByteArray(112,111,111,111,111,111,111,111,111,111,111,111,111,111,111,112)))

      val buffer = embedder.poll
      val bytes = buffer.array
      bytes.deep must ==(ByteArray(0,17, 97, 112,111,111,111,111,111,111,111,111,111,111,111,111,111,111,112).deep)
    }
  }
}
