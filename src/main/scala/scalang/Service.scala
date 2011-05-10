package scalang

import node._
import OtpConversions._
import com.ericsson.otp.erlang._
import org.jetlang._
import channels._
import core._
import fibers._
import scalang.util.Log
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

/**
 * Service is a class that provides a responder in the shape expected
 * by gen_lb.  A service may define handlers for either cast, call, or both.
 */
abstract class Service(ctx : ProcessContext) extends Process(ctx) {
  
  /**
   * Init callback for any context that the service might need.
   */
  def init(args : Any) {
    // noop
  }
  
  /**
   * Handle a call style of message which will expect a response.
   */
  def handleCall(from : Pid, request : Any) : Any = {
    throw new Exception(getClass + " did not define a call handler.")
  }
  
  /**
   * Handle a cast style of message which will receive no response.
   */
  def handleCast(request : Any) {
    throw new Exception(getClass + " did not define a cast handler.")
  }
  
  /**
   * Handle any messages that do not fit the call or cast pattern.
   */
  def handleInfo(request : Any)
  
  override def onMessage(msg : Any) = msg match {
    case ('ping, from : Pid, ref : Reference) =>
      from ! ('pong, ref)
    case (Symbol("$gen_call"), (from : Pid, ref : Reference), request : Any) =>
      val reply = handleCall(from, request)
      from ! (ref, reply)
    case (Symbol("$gen_cast"), request : Any) =>
      handleCast(request)
    case _ =>
      handleInfo(msg)
  }
  
  def makeCall(msg : Any) : (Reference,Any) = {
    val ref = makeRef
    val call = (Symbol("$gen_call"), (self, ref), msg)
    (ref, call)
  }
  
  def waitReply(ref : Reference, timeout : Long) : Any = {
    val queue = new LinkedBlockingQueue[Any]
    replyRegistry.registerReplyQueue(self, ref, queue)
    queue.poll(timeout, TimeUnit.MILLISECONDS)
  }
  
  def call(service : Pid, msg : Any) : Any = {
    call(service, msg, Long.MaxValue)
  }
  
  def call(service : Pid, msg : Any, timeout : Long) : Any = {
    val (ref, c) = makeCall(msg)
    service ! c
    waitReply(ref,timeout)
  }
  
  def call(service : Symbol, msg : Any) : Any = {
    call(service, msg, Long.MaxValue)
  }
  
  def call(service : Symbol, msg : Any, timeout : Long) : Any = {
    val (ref, c) = makeCall(msg)
    service ! c
    waitReply(ref,timeout)
  }
  
  def call(service : (Symbol,Symbol), msg : Any) : Any = {
    call(service, msg)
  }
  
  def call(service : (Symbol,Symbol), msg : Any, timeout : Long) : Any = {
    val (ref, c) = makeCall(msg)
    service ! c
    waitReply(ref,timeout)
  }
}