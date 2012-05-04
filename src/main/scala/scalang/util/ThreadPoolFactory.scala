package scalang.util

import overlock.threadpool._
import java.util.concurrent._
import atomic._
import org.jetlang.core._

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

class DefaultThreadPoolFactory extends ThreadPoolFactory {
  val cpus = Runtime.getRuntime.availableProcessors
  val max_threads = if ((2 * cpus) < 8) 8 else 2*cpus

  lazy val bossPool = ThreadPool.instrumentedElastic("scalang", "boss", 2, max_threads)
  lazy val workerPool = ThreadPool.instrumentedElastic("scalang", "worker", 2, max_threads)
  lazy val actorPool = ThreadPool.instrumentedElastic("scalang", "actor", 2, max_threads)
  lazy val batchExecutor = new BatchExecutorImpl

  val poolNameCounter = new AtomicInteger(0)

  def createBossPool : Executor = {
    bossPool
  }

  def createWorkerPool : Executor = {
    workerPool
  }

  def createActorPool : Executor = {
    actorPool
  }

  def createBatchExecutor(name : String, reentrant : Boolean) : BatchExecutor = {
    if (reentrant) {
      val queue = new ElasticBlockingQueue[Runnable]
      val pool = new BatchPoolExecutor("scalang", name, 1, max_threads, 60l, TimeUnit.SECONDS, queue, new NamedThreadFactory(name))
      queue.executor = pool
      pool
    } else {
      batchExecutor
    }
  }

  def createBatchExecutor(reentrant : Boolean) : BatchExecutor = {
    createBatchExecutor("pool-" + poolNameCounter.getAndIncrement, reentrant)
  }
}
