//
// Copyright 2011, Boundary
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package scalang.node

import org.jboss.{netty => netty}
import netty.channel._
import scalang._
import util._
import java.util.ArrayDeque
import scala.math._
import scala.collection.JavaConversions._
import java.security.{SecureRandom,MessageDigest}
import com.boundary.logula.Logging

abstract class HandshakeHandler(posthandshake : (Symbol,ChannelPipeline) => Unit) extends SimpleChannelHandler with StateMachine with Logging {
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
    log.error("Channel closed during handshake")
    handshakeFailed
  }

  override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent) {
    this.ctx = ctx
    log.error(e.getCause, "Exception caught during erlang handshake: ")
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
    posthandshake(peer,p)

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
