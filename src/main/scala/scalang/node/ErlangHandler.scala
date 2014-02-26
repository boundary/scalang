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

import org.jboss.netty._
import channel._
import scalang._
import com.boundary.logula.Logging

class ErlangHandler(
    node : ErlangNode,
    afterHandshake : Channel => Unit = { _ => Unit }) extends SimpleChannelUpstreamHandler with Logging {

  @volatile var peer : Symbol = null

  override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent) {
    log.error(e.getCause, "error caught in erlang handler %s", peer)
    ctx.getChannel.close
  }

  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
    val msg = e.getMessage
    log.debug("handler message %s", msg)
    msg match {
      case Tick =>
        ctx.getChannel.write(Tock) //channel heartbeat for erlang
      case HandshakeFailed(name) =>
        //not much we can do here?
        ctx.getChannel.close
      case HandshakeSucceeded(name, channel) =>
        peer = name
        node.registerConnection(name, channel)
        afterHandshake(channel)
      case LinkMessage(from, to) =>
        log.debug("received link request from %s.", from)
        node.linkWithoutNotify(from, to, e.getChannel)
      case SendMessage(to, msg) =>
        node.handleSend(to, msg)
      case ExitMessage(from, to, reason) =>
        node.remoteBreak(Link(from, to), reason)
      case Exit2Message(from, to, reason) =>
        node.remoteBreak(Link(from, to), reason)
      case UnlinkMessage(from, to) =>
        node.unlink(from, to)
      case RegSend(from, to, msg) =>
        node.handleSend(to, msg)
      case MonitorMessage(monitoring, monitored, ref) =>
        node.monitorWithoutNotify(monitoring, monitored, ref, e.getChannel)
      case DemonitorMessage(monitoring, monitored, ref) =>
        node.demonitor(monitoring, monitored, ref)
      case MonitorExitMessage(monitored, monitoring, ref, reason) =>
        node.remoteMonitorExit(Monitor(monitoring, monitored, ref), reason)
    }
  }

  override def channelDisconnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
    log.info("channel disconnected %s %s. peer: %s", ctx, e, peer)
    if (peer != null) {
      node.disconnected(peer, e.getChannel)
    }
  }

}
