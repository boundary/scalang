//
// Copyright 2012, Boundary
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

import scalang._
import com.yammer.metrics.scala._
import org.jetlang.fibers._
import org.jetlang.channels._
import org.jetlang.core._
import java.util.concurrent.TimeUnit
import org.cliffc.high_scale_lib.NonBlockingHashSet
import org.cliffc.high_scale_lib.NonBlockingHashMap
import scala.collection.JavaConversions._
import com.boundary.logula.Logging

abstract class ProcessHolder(ctx : ProcessContext) extends ProcessAdapter {
  val self = ctx.pid
  val fiber = ctx.fiber
  val messageRate = metrics.meter("messages", "messages", instrumentedName)
  val executionTimer = metrics.timer("execution", instrumentedName)
  def process : ProcessLike
  
  val msgChannel = new MemoryChannel[Any]
  msgChannel.subscribe(fiber, new Callback[Any] {
    def onMessage(msg : Any) {
      executionTimer.time {
        try {
          process.handleMessage(msg)
        } catch {
          case e : Throwable =>
            log.error(e, "An error occurred in actor %s", process)
            process.exit(e.getMessage)
        }
      }
    }
  })
  
  val exitChannel = new MemoryChannel[(Pid,Any)]
  exitChannel.subscribe(fiber, new Callback[(Pid,Any)] {
    def onMessage(msg : (Pid,Any)) {
      try {
        process.handleExit(msg._1, msg._2)
      } catch {
        case e : Throwable =>
          log.error(e, "An error occurred during handleExit in actor %s", this)
          process.exit(e.getMessage)
      }
    }
  })
  
  val monitorChannel = new MemoryChannel[(Any,Reference,Any)]
  monitorChannel.subscribe(fiber, new Callback[(Any,Reference,Any)] {
    def onMessage(msg : (Any,Reference,Any)) {
      try {
        process.handleMonitorExit(msg._1, msg._2, msg._3)
      } catch {
        case e : Throwable =>
          log.error(e, "An error occurred during handleMonitorExit in actor %s", this)
          process.exit(e.getMessage)
      }
    }
  })
  
  override def handleMessage(msg : Any) {
    messageRate.mark
    msgChannel.publish(msg)
  }

  override def handleExit(from : Pid, msg : Any) {
    exitChannel.publish((from,msg))
  }

  override def handleMonitorExit(monitored : Any, ref : Reference, reason : Any) {
    monitorChannel.publish((monitored,ref,reason))
  }
  
  def cleanup {
    fiber.dispose
    metricsRegistry.removeMetric(getClass, "messages", instrumentedName)
    metricsRegistry.removeMetric(getClass, "execution", instrumentedName)
  }
}

trait ProcessAdapter extends ExitListenable with SendListenable with LinkListenable with MonitorListenable with Instrumented with Logging {
  var state = 'alive
  def self : Pid
  def fiber : Fiber
  def referenceCounter : ReferenceCounter
  val links = new NonBlockingHashSet[Link]
  val monitors = new NonBlockingHashMap[Reference, Monitor]  
  def instrumentedName = self.toErlangString
  def cleanup
  
  def handleMessage(msg : Any)
  def handleExit(from : Pid, msg : Any)
  def handleMonitorExit(monitored : Any, ref : Reference, reason : Any)
  
  def exit(reason : Any) {
    synchronized {
      if (state != 'alive) return
      state = 'dead
    }

    // Exit listeners first, so that process is removed from table.
    for(e <- exitListeners) {
      e.handleExit(self, reason)
    }
    for (link <- links) {
      link.break(reason)
    }
    for (m <- monitors.values) {
      m.monitorExit(reason)
    }
    cleanup
  }
  
  def unlink(to : Pid) {
    links.remove(Link(self, to))
  }
  
  def link(to : Pid) {
    val l = registerLink(to)
    for (listener <- linkListeners) {
      listener.deliverLink(l)
    }
  }
  
  def registerLink(to : Pid) : Link = {
    val l = Link(self, to)
    for (listener <- linkListeners) {
      l.addLinkListener(listener)
    }
    synchronized {
      if (state != 'alive)
        l.break('noproc)
      else
        links.add(l)
    }
    l
  }

  def monitor(monitored : Any) : Reference = {
    val m = Monitor(self, monitored, makeRef)
    for (listener <- monitorListeners) {
      listener.deliverMonitor(m)
    }
    m.ref
  }
  
  def demonitor(ref : Reference) {
    monitors.remove(ref)
  }
  
  def registerMonitor(monitoring : Pid, ref : Reference): Monitor = {
    registerMonitor(Monitor(monitoring, self, ref))
  }

  private def registerMonitor(m : Monitor): Monitor = {
    for (listener <- monitorListeners) {
      m.addMonitorListener(listener)
    }
    synchronized {
      if (state != 'alive)
        m.monitorExit('noproc)
      else
        monitors.put(m.ref, m)
    }
    m
  }
  
  def makeRef = referenceCounter.makeRef
  
  def sendEvery(pid : Pid, msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run = notifySend(pid,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendEvery(name : Symbol, msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run = notifySend(name,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendEvery(name : (Symbol,Symbol), msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run = notifySend(name,self,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(pid : Pid, msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run {
        notifySend(pid, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(name : Symbol, msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run {
        notifySend(name, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(dest : (Symbol,Symbol), msg : Any, delay : Long) {
    val runnable = new Runnable {
      def run {
        notifySend(dest, self, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }
}
