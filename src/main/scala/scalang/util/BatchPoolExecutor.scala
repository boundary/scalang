package scalang.util

import org.jetlang.core._
import java.util._
import java.util.concurrent._
import overlock.threadpool._

class BatchPoolExecutor(path : String,
  name : String,
  coreSize : Int,
  maxSize : Int,
  keepAlive : Long,
  unit : TimeUnit,
  queue : BlockingQueue[Runnable],
  factory : ThreadFactory) extends
    InstrumentedThreadPoolExecutor(path, name, coreSize, maxSize, keepAlive, unit, queue, factory) with
    BatchExecutor {

  override def execute(reader : EventReader) {

    // build a job which will run available work sequentially in a separate thread

    val tasks = new ArrayList[Runnable](reader.size())
    var i = 0
    while(i < reader.size()) {
      tasks.add(reader.get(i))
      i = i + 1
    }

    val job =
      new Runnable {
        def run() {
          val ti = tasks.iterator()
          while(ti.hasNext) {
            ti.next().run()
          }
        }
      }

    execute(job)
  }
}
