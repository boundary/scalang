package scalang.node

import java.net._
import java.util.concurrent._
import atomic._
import org.jboss.{netty => netty}
import netty.bootstrap._
import netty.channel._
import netty.handler.codec.frame._
import scalang._
import util._
import java.util.ArrayDeque
import scala.annotation._
import scala.math._
import scala.collection.JavaConversions._
import java.security.{SecureRandom,MessageDigest}

class ClientHandshakeHandler(name : String, cookie : String) extends HandshakeHandler {
  states(
    state('disconnected, {
      case ConnectedMessage => 
        sendName
        'connected
    }),
    
    state('connected, {
      case StatusMessage("ok") =>
        'status_ok
      case StatusMessage(status) =>
        throw new ErlangAuthException("Bad status message: " + status)
    }),
    
    state('status_ok, {
      case ChallengeMessage(version, flags, c) =>
        sendChallengeReply(c)
        'reply_sent
    }),
    
    state('reply_sent, {
      case ChallengeAckMessage(digest) =>
        verifyChallengeAck(digest)
        'verified
    }),
    
    state('verified, {
      case _ => 'verified
    }))
    
  protected def sendName {
    val channel = ctx.getChannel
    val future = Channels.future(channel)
    val msg = NameMessage(5, DistributionFlags.default, name)
    ctx.sendDownstream(new DownstreamMessageEvent(channel,future,msg,null))
  }
    
  protected def sendChallengeReply(c : Int) {
    val channel = ctx.getChannel
    val future = Channels.future(channel)
    this.peerChallenge = c
    this.challenge = random.nextInt
    val d = digest(peerChallenge, cookie)
    val msg = ChallengeReplyMessage(challenge, d)
    ctx.sendDownstream(new DownstreamMessageEvent(channel,future,msg,null))
  }
  
  protected def verifyChallengeAck(peerDigest : Array[Byte]) {
    val ourDigest = digest(challenge, cookie)
    if (!digestEquals(ourDigest, peerDigest)) {
      throw new ErlangAuthException("Peer authentication error.")
    }
  }
}