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

abstract class HandshakeHandler extends SimpleChannelHandler with StateMachine with Log {
  override val start = 'disconnected
  @volatile var ctx : ChannelHandlerContext = null
  @volatile var peer : Symbol = null
  @volatile var challenge : Int = 0
  @volatile var peerChallenge : Int = 0
  
  val messages = new ArrayDeque[MessageEvent]
  val random = SecureRandom.getInstance("SHA1PRNG")
  
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
    this.ctx = ctx
    error("Exception caught during erlang handshake: ", e.getCause)
    handshakeFailed
  }
  
  override def writeRequested(ctx : ChannelHandlerContext, e : MessageEvent) {
    this.ctx = ctx
    if (isVerified) {
      super.writeRequested(ctx,e)
    } else {
      messages.offer(e)
    }
  }
  
  //utility methods
  protected def digest(challenge : Int, cookie : String) : Array[Byte] = {
    val masked = mask(challenge)
    val md5 = MessageDigest.getInstance("MD5")
    md5.update(cookie.getBytes)
    md5.update(masked.toString.getBytes)
    md5.digest
  }
  
  def mask(challenge : Int) : Long = {
    if (challenge < 0) {
      (1L << 31) | (challenge & 0x7FFFFFFFL)
    } else {
      challenge.toLong
    }
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
    val p = ctx.getPipeline
    val keys = p.toMap.keySet
    for (name <- List("handshakeFramer", "handshakeDecoder", "handshakeEncoder", "handshakeHandler"); if keys.contains(name)) {
      p.remove(name)
    }
    for (msg <- messages) {
      ctx.sendDownstream(msg)
    }
    messages.clear
  }
  
  protected def handshakeSucceeded {
    ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel, HandshakeSucceeded(peer, ctx.getChannel), null))
  }
  
  protected def handshakeFailed {
    ctx.getChannel.close
    ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel, HandshakeFailed(peer), null))
  }
}