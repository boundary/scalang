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

class ServerHandshakeHandler(node : Symbol, cookie : String) extends HandshakeHandler {
  states(
    state('disconnected, { 
      case ConnectedMessage => 'connected
    }),
    
    state('connected, { 
      case msg : NameMessage =>
        receiveName(msg)
        sendStatus
        sendChallenge
        'challenge_sent
    }),
    
    state('challenge_sent, { 
      case msg : ChallengeReplyMessage =>
        verifyChallenge(msg)
        sendChallengeAck(msg)
        drainQueue
        'verified
    }),
    
    state('verified, { case _ => 'verified}))

  //state machine callbacks
  protected def receiveName(msg : NameMessage) {
    peer = ErlangPeer(msg.name)
  }
  
  protected def sendStatus {
    val channel = ctx.getChannel
    val future = Channels.future(channel)
    ctx.sendDownstream(new DownstreamMessageEvent(channel,future,StatusMessage("ok"),null))
  }
  
  protected def sendChallenge {
    val channel = ctx.getChannel
    val future = Channels.future(channel)
    challenge = random.nextInt
    val msg = ChallengeMessage(5, DistributionFlags.default, challenge, node.name)
    ctx.sendDownstream(new DownstreamMessageEvent(channel,future,msg,null))
  }
  
  protected def verifyChallenge(msg : ChallengeReplyMessage) {
    val ourDigest = digest(challenge, cookie)
    if (!digestEquals(ourDigest, msg.digest)) {
      throw new ErlangAuthException("Peer authentication error.")
    }
  }
  
  protected def sendChallengeAck(msg : ChallengeReplyMessage) {
    val channel = ctx.getChannel
    val future = Channels.future(channel)
    val md5 = digest(msg.challenge, cookie)
    val msgOut = ChallengeAckMessage(md5)
    ctx.sendDownstream(new DownstreamMessageEvent(channel,future,msgOut,null))
  }
}