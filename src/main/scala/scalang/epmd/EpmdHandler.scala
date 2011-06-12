package scalang.epmd

import java.net._
import java.util.concurrent.{ConcurrentLinkedQueue, Callable, CountDownLatch, atomic => atomic}
import atomic._
import org.jboss.{netty => netty}
import netty.bootstrap._
import netty.channel._
import netty.handler.codec.frame._
import scala.collection.JavaConversions._
import com.codahale.logula.Logging
import java.util.concurrent.TimeUnit

class EpmdHandler extends SimpleChannelUpstreamHandler with Logging {
  val queue = new ConcurrentLinkedQueue[EpmdResponse]
  
  def response : Callable[Any] = {
    val call = new EpmdResponse
    queue.add(call)
    call
  }
  
  override def channelClosed(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
    log.debug("Oh snap channel closed.")
  }
  
  override def channelDisconnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
    log.debug("Uh oh disconnect.")
  }
  
  override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent) {
    var rsp = queue.poll
    while (rsp != null) {
      rsp.setError(e.getCause)
      rsp = queue.poll
    }
  }
  
  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
    val response = e.getMessage
    var rsp = queue.poll
    while (rsp != null) {
      rsp.set(response)
      rsp = queue.poll
    }
  }
  
  class EpmdResponse extends Callable[Any] {
    val response = new AtomicReference[Any]
    val error = new AtomicReference[Throwable]
    val lock = new CountDownLatch(1)
    
    def setError(t : Throwable) {
      error.set(t)
      lock.countDown
      
    }
    
    def set(v : Any) {
      response.set(v)
      lock.countDown
    }
    
    def call : Any = {
      if (lock.await(500, TimeUnit.MILLISECONDS)) {
        if (error.get != null) {
          throw new Exception("EPMD Registration failed.", error.get)
        } else {
          response.get
        }
      } else {
        throw new Exception("EPMD Registration timed out.")
      }
    }
  }
}