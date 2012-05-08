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

class ClientHandshakeHandler(name : Symbol, cookie : String, posthandshake : (Symbol,ChannelPipeline) => Unit) extends HandshakeHandler(posthandshake) {
  states(
    state('disconnected, {
      case ConnectedMessage =>
        sendName
        'connected
    }),

    state('connected, {
      case StatusMessage("ok") =>
        'status_ok
      case StatusMessage("ok_simultaneous") =>
        'status_ok
      case StatusMessage("alive") => //means the other node sees another conn from us. reconnecting too quick.
        sendStatus("true")
        'status_ok
      case StatusMessage(status) =>
        throw new ErlangAuthException("Bad status message: " + status)
    }),

    state('status_ok, {
      case ChallengeMessage(version, flags, c, name) =>
        peer = Symbol(name)
        sendChallengeReply(c)
        'reply_sent
    }),

    state('reply_sent, {
      case ChallengeAckMessage(digest) =>
        verifyChallengeAck(digest)
        drainQueue
        handshakeSucceeded
        'verified
    }),

    state('verified, {
      case _ => 'verified
    }))

  protected def sendStatus(st : String) {
    val channel = ctx.getChannel
    val future = Channels.future(channel)
    val msg = StatusMessage(st)
    ctx.sendDownstream(new DownstreamMessageEvent(channel,future,msg,null))
  }

  protected def sendName {
    val channel = ctx.getChannel
    val future = Channels.future(channel)
    val msg = NameMessage(5, DistributionFlags.default, name.name)
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
