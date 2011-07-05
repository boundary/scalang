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
    execute(new Runnable {
      def run {
        var i = 0
        while (i < reader.size) {
          reader.get(i).run
          i += 1
        }
      }
    })
  }
}
