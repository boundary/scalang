package scalang.node

import java.net._
import java.util.concurrent._
import atomic._
import org.jboss.netty._
import bootstrap._
import channel._
import scalang._

class ErlangHandler(node : ErlangNode) extends SimpleChannelUpstreamHandler {
  
  @volatile var peer : Symbol = null
  
  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
    e.getMessage match {
      case Tick =>
/*        println("got tick")*/
        ctx.getChannel.write(Tock) //channel heartbeat for erlang
      case HandshakeFailed(name) =>
        //not much we can do here?
      case HandshakeSucceeded(name, channel) =>
/*        println("registering connection to " + name)*/
        peer = name
        node.registerConnection(name, channel)
      case LinkMessage(from, to) =>
        node.link(from, to)
      case SendMessage(to, msg) =>
        node.handleSend(to, msg)
      case ExitMessage(from, to, reason) =>
        node.break(from, to, reason)
      case Exit2Message(from, to, reason) =>
        node.break(from, to, reason)
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