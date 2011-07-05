package scalang.node

import java.net._
import java.util.concurrent._
import atomic._
import org.jboss.netty._
import bootstrap._
import channel._
import scalang._
import com.codahale.logula.Logging

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
        node.link(from, to)
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
    }
  }
  
  override def channelDisconnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
    if (peer != null) {
      node.disconnected(peer)
    }
  }
  
}