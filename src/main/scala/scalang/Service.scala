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
package scalang

import node._
import org.jetlang._
import channels._
import core._
import fibers._
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

/**
 * Service is a class that provides a responder in the shape expected
 * by gen_lb.  A service may define handlers for either cast, call, or both.
 */
abstract class Service[A <: Product](ctx : ServiceContext[A]) extends Process(ctx) {
  /**
   * Init callback for any context that the service might need.
   */
/*  def init(args : Any) {
    // noop
  }
*/
  /**
   * Handle a call style of message which will expect a response.
   */
  def handleCall(tag : (Pid,Reference), request : Any) : Any = {
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
  def handleInfo(request : Any) {
    throw new Exception(getClass + " did not define an info handler.")
  }

  override def onMessage(msg : Any) = msg match {
    case ('ping, from : Pid, ref : Reference) =>
      from ! ('pong, ref)
    case (Symbol("$gen_call"), (from : Pid, ref : Reference), request : Any) =>
      handleCall((from, ref), request) match {
        case ('reply, reply) =>
          from ! (ref, reply)
        case 'noreply =>
          Unit
        case reply =>
          from ! (ref, reply)
      }
    case (Symbol("$gen_cast"), request : Any) =>
      handleCast(request)
    case _ =>
      handleInfo(msg)
  }

  def call(to : Pid, msg : Any) : Any = node.call(self,to,msg)
  def call(to : Pid, msg : Any, timeout : Long) : Any = node.call(self,to,msg,timeout)
  def call(to : Symbol, msg : Any) : Any = node.call(self,to,msg)
  def call(to : Symbol, msg : Any, timeout : Long) : Any = node.call(self,to,msg,timeout)
  def call(to : (Symbol,Symbol), msg : Any) : Any = node.call(self,to,msg)
  def call(to : (Symbol,Symbol), msg : Any, timeout : Long) : Any = node.call(self,to,msg,timeout)

  def cast(to : Pid, msg : Any) = node.cast(to,msg)
  def cast(to : Symbol, msg : Any) = node.cast(to,msg)
  def cast(to : (Symbol,Symbol), msg : Any) = node.cast(to,msg)

}
