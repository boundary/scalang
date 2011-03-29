package scalang.node

import org.specs._
import scalang.util._
import org.jboss.{netty => netty}
import netty.handler.codec.embedder._
import netty.buffer._
import ChannelBuffers._
import java.security.MessageDigest

class ServerHandshakeHandlerSpec extends Specification {
  val cookie = "DRSJLFJLGIYPEAVFYFCY"
  
  "ServerHandshakeHandler" should {
    "complete a standard handshake" in {
      val handshake = new ServerHandshakeHandler(Symbol("tmp@blah"), cookie)
      val embedder = new TwoWayCodecEmbedder[Any](handshake)
      embedder.upstreamMessage(NameMessage(5, 32765, "tmp@moonpolysoft.local"))
      val status = embedder.poll
      status must ==(StatusMessage("ok"))
      var challenge = 0
      val challengeMsg = embedder.poll
      challengeMsg must beLike { case ChallengeMessage(5, _, c : Int, _) => 
        challenge = c
        true }
      println("challenge " + challenge)
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