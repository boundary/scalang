package scalang.node

import scalang._
import org.specs._
import org.specs.runner._
import org.specs.mock.Mockito
import org.mockito.Matchers._
import scalang.util._
import org.jboss.{netty => netty}
import netty.buffer._
import netty.channel._
import ChannelBuffers._
import java.security.MessageDigest
import netty.handler.codec.embedder.TwoWayCodecEmbedder

class ServerHandshakeHandlerSpec extends SpecificationWithJUnit with Mockito {
  val cookie = "DRSJLFJLGIYPEAVFYFCY"
  val nodeName = Symbol("tmp@blah")

  "ServerHandshakeHandler" should {
    "complete a standard handshake" in {
      val node = mock[ErlangNode]
      node.cookie returns cookie
      node.name returns nodeName
      node.registerConnection(any[Symbol],any[ChannelFuture],any[Channel]) returns 'ok
      val handshake = new ServerHandshakeHandler(node)
      val embedder = new TwoWayCodecEmbedder[Any](handshake)
      embedder.upstreamMessage(NameMessage(5, 32765, "tmp@moonpolysoft.local"))
      val status = embedder.poll
      status must ==(StatusMessage("ok"))
      var challenge = 0
      val challengeMsg = embedder.poll
      challengeMsg must beLike { case ChallengeMessage(5, _, c : Int, _) =>
        challenge = c
        true }
      val md5 = MessageDigest.getInstance("MD5")
      md5.update(cookie.getBytes)
      md5.update(handshake.mask(challenge).toString.getBytes)
      val digest = md5.digest
      //we reuse the same challenge to make the test easier
      embedder.upstreamMessage(ChallengeReplyMessage(challenge, digest))
      val ackMsg = embedder.poll
      ackMsg must beLike { case ChallengeAckMessage(d) =>
        d.deep == digest.deep
      }
      handshake.isVerified must ==(true)
    }
  }
}
