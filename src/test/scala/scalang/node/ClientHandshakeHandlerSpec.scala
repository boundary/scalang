package scalang.node

import org.specs._
import scalang.util._
import org.jboss.{netty => netty}
import netty.handler.codec.embedder._
import netty.buffer._
import ChannelBuffers._
import java.security.MessageDigest

class ClientHandshakeHandlerSpec extends Specification {
  val cookie = "DRSJLFJLGIYPEAVFYFCY"
  val node = "tmp@moonpolysoft.local"
  
  "ClientHandshakeHandler" should {
    "complete a standard handshake" in {
      val handshake = new ClientHandshakeHandler(node, cookie)
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
      md5.update(challenge.toString.getBytes)
      embedder.upstreamMessage(ChallengeAckMessage(md5.digest))
      handshake.isVerified must ==(true)
    }
  }
}