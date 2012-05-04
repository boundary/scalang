package scalang.node

import org.jboss.netty
import netty.bootstrap._
import netty.channel._
import netty.handler.codec.frame._
import com.yammer.metrics.scala._
import java.util.concurrent._

class PacketCounter(name : String) extends SimpleChannelHandler with Instrumented {
  val ingress = metrics.meter("ingress", "packets", name, TimeUnit.SECONDS)
  val egress = metrics.meter("egress", "packets", name, TimeUnit.SECONDS)
  val exceptions = metrics.meter("exceptions", "exceptions", name, TimeUnit.SECONDS)

  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
    ingress.mark
    super.messageReceived(ctx, e)
  }

  override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent) {
     exceptions.mark
     super.exceptionCaught(ctx, e)
  }

  override def writeRequested(ctx : ChannelHandlerContext, e : MessageEvent) {
    egress.mark
    super.writeRequested(ctx, e)
  }
}
