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

class ServerHandshakeHandler(name : Symbol, cookie : String, posthandshake : (Symbol,ChannelPipeline) => Unit) extends HandshakeHandler(posthandshake) {
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
        handshakeSucceeded
        'verified
    }),

    state('verified, { case _ => 'verified}))

  //state machine callbacks
  protected def receiveName(msg : NameMessage) {
    peer = Symbol(msg.name)
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
    val msg = ChallengeMessage(5, DistributionFlags.default, challenge, name.name)
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
