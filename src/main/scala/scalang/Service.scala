package scalang

import node._
import OtpConversions._
import com.ericsson.otp.erlang._
import org.jetlang._
import channels._
import core._
import fibers._
import scalang.util.Log

/**
 * Service is a class that provides a responder in the shape expected
 * by gen_lb.  A service may define handlers for either cast, call, or both.
 */
abstract class Service(ctx : ProcessContext) extends Process(ctx) {
  
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
  
  override def onMessage(msg : Any) = msg match {
    case ('ping, from : Pid, ref : Reference) =>
      from ! ('pong, ref)
    case (Symbol("$gen_call"), (from : Pid, ref : Reference), request : Any) =>
      val reply = handleCall(from, request)
      from ! (ref, reply)
    case (Symbol("$gen_cast"), request : Any) =>
      handleCast(request)
  }
}