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

import org.jetlang.fibers._
import org.cliffc.high_scale_lib._
import scalang.node.{ExitListener, SendListener, ProcessLike}
import org.jetlang.channels._
import org.jetlang.core._
import com.codahale.logula.Logging
import java.util.concurrent.TimeUnit
import com.yammer.metrics.scala._
import scala._

abstract class Process(ctx : ProcessContext) extends ProcessLike with Logging with Instrumented {
  val self = ctx.pid
  val fiber = ctx.fiber
  val referenceCounter = ctx.referenceCounter
  val replyRegistry = ctx.replyRegistry
  val node = ctx.node
  val messageRate = metrics.meter("messages", "messages", instrumentedName)
  val executionTimer = metrics.timer("execution", instrumentedName)

  def instrumentedName = self.toErlangString

  implicit def pid2sendable(pid : Pid) = new PidSend(pid,this)
  implicit def sym2sendable(to : Symbol) = new SymSend(to,this)
  implicit def dest2sendable(dest : (Symbol,Symbol)) = new DestSend(dest,self,this)

  def sendEvery(pid : Pid, msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run = send(pid,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendEvery(name : Symbol, msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run = send(name,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendEvery(name : (Symbol,Symbol), msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run = send(name,self,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(pid : Pid, msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run {
        send(pid, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(name : Symbol, msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run {
        send(name, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(dest : (Symbol,Symbol), msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run {
        send(dest, self, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }

  /**
   * Subclasses should override this method with their own message handlers
   */
  def onMessage(msg : Any)

  /**
   * Subclasses wishing to trap exits should override this method.
   */
  def trapExit(from : Pid, msg : Any) {
    exit(msg)
  }

  /**
   * Subclasses wishing to trap monitor exits should override this method.
   */
  def trapMonitorExit(monitored : Pid, ref : Reference, reason : Any) {
  }

  override def handleMessage(msg : Any) {
    messageRate.mark
    msgChannel.publish(msg)
  }

  override def handleExit(from : Pid, msg : Any) {
    exitChannel.publish((from,msg))
  }

  override def handleMonitorExit(monitored : Pid, ref : Reference, reason : Any) {
    monitorChannel.publish((monitored,ref,reason))
  }

  val p = this
  val msgChannel = new MemoryChannel[Any]
  msgChannel.subscribe(ctx.fiber, new Callback[Any] {
    def onMessage(msg : Any) {
      executionTimer.time {
        try {
          p.onMessage(msg)
        } catch {
          case e : Throwable =>
            log.error(e, "An error occurred in actor %s", this)
            exit(e.getMessage)
        }
      }
    }
  })

  val exitChannel = new MemoryChannel[(Pid,Any)]
  exitChannel.subscribe(ctx.fiber, new Callback[(Pid,Any)] {
    def onMessage(msg : (Pid,Any)) {
      try {
        trapExit(msg._1, msg._2)
      } catch {
        case e : Throwable =>
          log.error(e, "An error occurred during handleExit in actor %s", this)
          exit(e.getMessage)
      }
    }
  })

  val monitorChannel = new MemoryChannel[(Pid,Reference,Any)]
  monitorChannel.subscribe(ctx.fiber, new Callback[(Pid,Reference,Any)] {
    def onMessage(msg : (Pid,Reference,Any)) {
      try {
        trapMonitorExit(msg._1, msg._2, msg._3)
      } catch {
        case e : Throwable =>
          log.error(e, "An error occurred during handleMonitorExit in actor %s", this)
          exit(e.getMessage)
      }
    }
  })

}

class PidSend(to : Pid, proc : Process) {
  def !(msg : Any) {
    proc.notifySend(to,msg)
  }
}

class SymSend(to : Symbol, proc : Process) {
  def !(msg : Any) {
    proc.notifySend(to, msg)
  }
}

class DestSend(to : (Symbol,Symbol), from : Pid, proc : Process) {
  def !(msg : Any) {
    proc.notifySend(to, from, msg)
  }
}
