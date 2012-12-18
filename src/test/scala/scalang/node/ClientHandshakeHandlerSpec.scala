package scalang.node

import scalang._
import org.specs._
import org.specs.runner._
import org.specs.mock.Mockito
import org.mockito.Matchers._
import org.jboss.{netty => netty}
import netty.buffer._
import netty.channel._
import java.security.MessageDigest
import netty.handler.codec.embedder.TwoWayCodecEmbedder

class ClientHandshakeHandlerSpec extends SpecificationWithJUnit with Mockito {
  val cookie = "DRSJLFJLGIYPEAVFYFCY"
  val nodeName = Symbol("tmp@moonpolysoft.local")

  "ClientHandshakeHandler" should {
    "complete a standard handshake" in {
      val node = mock[ErlangNode]
      node.cookie returns cookie
      node.name returns nodeName
      val handshake = new ClientHandshakeHandler(Symbol("tmp@blah"), node)
      val embedder = new TwoWayCodecEmbedder[Any](handshake)
      val nameMsg = embedder.poll
      nameMsg must beLike { case NameMessage(5, _, node) => true }
      embedder.upstreamMessage(StatusMessage("ok"))
      embedder.upstreamMessage(ChallengeMessage(5, 32765, 15000, "tmp@blah"))
      val respMsg = embedder.poll
      var challenge = 0
      respMsg must beLike { case ChallengeReplyMessage(c, digest) =>
        challenge = c
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(cookie.getBytes)
        md5.update("15000".getBytes)
        digest.deep == md5.digest.deep
      }
      val md5 = MessageDigest.getInstance("MD5")
      md5.update(cookie.getBytes)
      md5.update(handshake.mask(challenge).toString.getBytes)
      embedder.upstreamMessage(ChallengeAckMessage(md5.digest))
      handshake.isVerified must ==(true)
    }
  }
}
