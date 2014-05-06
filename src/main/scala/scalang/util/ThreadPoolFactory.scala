package scalang.util

import overlock.threadpool._
import java.util.concurrent._
import atomic._
import org.jetlang.core._
import org.jboss.netty.handler.execution.{MemoryAwareThreadPoolExecutor, OrderedMemoryAwareThreadPoolExecutor}
import org.jboss.netty.util.ObjectSizeEstimator

object ThreadPoolFactory {
  @volatile var factory : ThreadPoolFactory = new DefaultThreadPoolFactory
}

trait ThreadPoolFactory {

  /**
   * This creates threadpool intended for use as the "boss" pool in netty connections.
   * The boss pool typically will only ever need one thread. It handles bookkeeping for netty.
   */
  def createBossPool : Executor

  /**
   * The netty worker pool. These thread pools deal with the actual connection handling threads
   * in netty.  If you want to tightly cap the number of threads that netty uses, only return a
   * single instance from this method.
   */
  def createWorkerPool : Executor

  def createExecutorPool : Executor

  /**
   * The jetlang actor pool.  This thread pool will be responsible for executing any actors
   * that are launched with an unthreaded batch executor.
   */
  def createActorPool : Executor

  /**
   * Batch exector for pool backed actors.  If this returns the default unthreaded executor then
   * only one thread will be active in an actor's onMessage method at a time.  If this returns a
   * a threadpool backed batch executor then multiple threads will be active in a single actor.
   */
  def createBatchExecutor(name : String, reentrant : Boolean) : BatchExecutor

  def createBatchExecutor(reentrant : Boolean) : BatchExecutor
}

object StupidObjectSizeEstimator extends ObjectSizeEstimator {
  def estimateSize(o : Any) = 1
}

class DefaultThreadPoolFactory extends ThreadPoolFactory {
  val cpus = Runtime.getRuntime.availableProcessors
  val max_threads = if ((2 * cpus) < 8) 8 else 2*cpus
  val max_memory = Runtime.getRuntime.maxMemory / 2
  //100 mb
  /*lazy val bossPool = new OrderedMemoryAwareThreadPoolExecutor(max_threads, 104857600, 104857600, 1000, TimeUnit.SECONDS, new NamedThreadFactory("boss"))
  //500 mb
  lazy val workerPool = new OrderedMemoryAwareThreadPoolExecutor(max_threads, 524288000, 524288000, 1000, TimeUnit.SECONDS, new NamedThreadFactory("worker"))
  lazy val actorPool = new MemoryAwareThreadPoolExecutor(max_threads, 100, 100, 1000, TimeUnit.SECONDS, StupidObjectSizeEstimator, new NamedThreadFactory("actor"))
  **/
  lazy val bossPool = ThreadPool.instrumentedFixed("scalang", "boss", max_threads)
  lazy val workerPool = ThreadPool.instrumentedFixed("scalang", "worker", max_threads)
  lazy val executorPool = new OrderedMemoryAwareThreadPoolExecutor(max_threads, max_memory, max_memory, 1000, TimeUnit.SECONDS, new NamedThreadFactory("executor"))
  lazy val actorPool = ThreadPool.instrumentedFixed("scalang", "actor", max_threads)
  lazy val batchExecutor = new BatchExecutorImpl

  val poolNameCounter = new AtomicInteger(0)

  def createBossPool : Executor = {
    bossPool
  }

  def createWorkerPool : Executor = {
    workerPool
  }
  
  def createExecutorPool : Executor = {
    executorPool
  }

  def createActorPool : Executor = {
    actorPool
  }

  def createBatchExecutor(name : String, reentrant : Boolean) : BatchExecutor = {
    if (reentrant) {
      val queue = new LinkedBlockingQueue[Runnable]
      val pool = new BatchPoolExecutor("scalang", name, max_threads, max_threads, 60l, TimeUnit.SECONDS, queue, new NamedThreadFactory(name))
      pool
    } else {
      batchExecutor
    }
  }

  def createBatchExecutor(reentrant : Boolean) : BatchExecutor = {
    createBatchExecutor("pool-" + poolNameCounter.getAndIncrement, reentrant)
  }
}
