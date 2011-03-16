package scalang.epmd

import java.net._
import java.util.concurrent.{ConcurrentLinkedQueue, Callable, CountDownLatch, atomic => atomic}
import atomic._
import org.jboss.{netty => netty}
import netty.bootstrap._
import netty.channel._
import netty.handler.codec.frame._
import scala.collection.JavaConversions._

class EpmdHandler extends SimpleChannelUpstreamHandler {
  val queue = new ConcurrentLinkedQueue[EpmdResponse]
  
  def response : Callable[Any] = {
    val call = new EpmdResponse
    queue.add(call)
    call
  }
  
  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
    val response = e.getMessage
    for(rspCallable <- queue) {
      rspCallable.set(response)
    }
    queue.clear
  }
  
  class EpmdResponse extends Callable[Any] {
    val response = new AtomicReference[Any]
    val lock = new CountDownLatch(1)
    
    def set(v : Any) {
      response.set(v)
      lock.countDown
    }
    
    def call : Any = {
      lock.await
      response.get
    }
  }
}