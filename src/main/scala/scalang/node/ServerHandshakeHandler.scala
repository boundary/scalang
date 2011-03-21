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

class ServerHandshakeHandler(cookie : String) extends SimpleChannelHandler with StateMachine with Log {
  override val start = 'disconnected
  @volatile var ctx : ChannelHandlerContext = null
  @volatile var peer : ErlangPeer = null
  @volatile var challenge : Int = 0
  @volatile var peerChallenge : Int = 0
  
  val messages = new ArrayDeque[MessageEvent]
  val random = SecureRandom.getInstance("SHA1PRNG")
  
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
  
  def isVerified = currentState == 'verified
  
  //handler callbacks
  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
    this.ctx = ctx
    val msg = e.getMessage
    if (isVerified) {
      super.messageReceived(ctx, e)
      return
    }
    
    event(msg)
  }
  
  override def channelConnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
    this.ctx = ctx
    val channel = ctx.getChannel
    val future = Channels.future(channel)
    event(ConnectedMessage)
  }
  
  override def channelClosed(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
    this.ctx = ctx
  }
  
  override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent) {
    error("Exception during erlang handshake.", e.getCause)
  }
  
  override def writeRequested(ctx : ChannelHandlerContext, e : MessageEvent) {
    this.ctx = ctx
    if (isVerified) {
      super.writeRequested(ctx,e)
    } else {
      messages.offer(e)
    }
    
  }
  
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
    val msg = ChallengeMessage(5, DistributionFlags.default, challenge)
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
  
  protected def digest(challenge : Int, cookie : String) : Array[Byte] = {
    val masked = if (challenge < 0) {
      (1L << 31) | (challenge & 0x7FFFFFFFL)
    } else {
      challenge.toLong
    }
    val md5 = MessageDigest.getInstance("MD5")
    md5.update(cookie.getBytes)
    md5.update(challenge.toString.getBytes)
    md5.digest
  }
  
  protected def digestEquals(a : Array[Byte], b : Array[Byte]) : Boolean = {
    var equals = true
    if (a.length != b.length) {
      equals = false
    }
    val length = min(a.length,b.length)
    for (i <- (0 until length)) {
      equals &&= (a(i) == b(i))
    }
    equals
  }
  
  protected def drainQueue {
    for (msg <- messages) {
      ctx.sendDownstream(msg)
    }
    messages.clear
    
    ctx.getPipeline.remove(this)
  }
  
  protected def handshakeSucceeded {
    ctx.getChannel.close
    ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel, HandshakeFailed, null))
  }
  
  protected def handshakeFailed {
    ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel, HandshakeSucceeded, null))
  }
}