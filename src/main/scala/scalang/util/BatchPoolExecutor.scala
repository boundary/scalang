package scalang.util

import concurrent.forkjoin.LinkedTransferQueue
import org.jetlang.core._
import java.util._
import java.util.concurrent._

class BatchPoolExecutor(coreSize : Int, maxSize : Int, keepAlive : Long) extends ThreadPoolExecutor(coreSize, maxSize, keepAlive, TimeUnit.MILLISECONDS, new LinkedTransferQueue[Runnable]) with BatchExecutor {
  override def execute(reader : EventReader) {
    var i = 0
    while (i < reader.size) {
      execute(reader.get(i))
      i += 1
    }
  }
}