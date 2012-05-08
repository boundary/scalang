package scalang.node

import org.specs._
import org.specs.runner._
import scalang.util._
import org.jboss.{netty => netty}
import netty.handler.codec.embedder._
import netty.buffer._
import netty.util._
import netty.handler.timeout._
import java.util.{Set => JSet}
import java.util.concurrent._

class FailureDetectionHandlerSpec extends SpecificationWithJUnit {
  class SeqClock(seq : Long*) extends Clock {
    val iterator = seq.iterator

    override def currentTimeMillis : Long = {
      iterator.next
    }
  }

  class MockTimer extends Timer {
    var t : Timeout = null
    val self = this

    def fire {
      if (t != null) t.getTask.run(t)
    }

    override def newTimeout(task : TimerTask, delay : Long, unit : TimeUnit) : Timeout = {
      t = new Timeout {
        def cancel {
          t = null
        }

        def getTask = task

        def getTimer = self

        def isCancelled = false

        def isExpired = false
      }
      t
    }

    override def stop : JSet[Timeout] = {
      null
    }
  }

  "FailureDetectionHandler" should {
    "cause an exception after 4 ticks" in {
      val timer = new MockTimer
      val clock = new SeqClock(1000,2000,3000,4000,5000)
      val handler = new FailureDetectionHandler(Symbol("test@localhost.local"), clock, 4, timer)
      val embedder = new DecoderEmbedder[Any](handler)
      timer.fire
      timer.fire
      timer.fire
      timer.fire must throwA[Exception]
    }

    "pass through messages" in {
      val timer = new MockTimer
      val clock = new SeqClock(1000,2000,3000,4000,5000)
      val handler = new FailureDetectionHandler(Symbol("test@localhost.local"), clock, 4, timer)
      val embedder = new DecoderEmbedder[Any](handler)
      embedder.offer(LinkMessage(null, null))
      embedder.poll must beLike { case LinkMessage(null, null) => true }
    }

    "tolerate occasional missed ticks" in {
      val timer = new MockTimer
      val clock = new SeqClock(1000,2000,3000,4000,5000,6000,7000)
      val handler = new FailureDetectionHandler(Symbol("test@localhost.local"), clock, 4, timer)
      val embedder = new DecoderEmbedder[Any](handler)
      timer.fire
      timer.fire
      embedder.offer(LinkMessage(null, null))
      embedder.poll must notBeNull
      timer.fire
      timer.fire
    }
  }
}
