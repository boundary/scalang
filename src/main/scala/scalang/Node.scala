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

import java.util.concurrent.atomic._
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.{netty => netty}
import netty.channel._
import netty.bootstrap._
import socket.nio.NioServerSocketChannelFactory
import java.util.concurrent._
import scalang.node._
import org.cliffc.high_scale_lib._
import overlock.atomicmap._
import org.jetlang._
import core._
import java.io._
import fibers.PoolFiberFactory
import core.BatchExecutorImpl
import scala.collection.JavaConversions._
import scalang.epmd._
import scalang.util._
import java.security.SecureRandom
import com.codahale.logula.Logging
import org.apache.log4j.Level
import org.jboss.netty.logging._
import netty.util.HashedWheelTimer
import com.yammer.metrics._

object Node {
  val random = SecureRandom.getInstance("SHA1PRNG")
  
  def apply(name : String) : Node = apply(Symbol(name))
  def apply(name : String, cookie : String) : Node = apply(Symbol(name), cookie)
  def apply(name : String, cookie : String, tpf : ThreadPoolFactory) : Node = apply(Symbol(name), cookie, tpf)
  def apply(name : String, cookie : String, listener : ClusterListener) : Node = apply(Symbol(name), cookie, listener)
  def apply(name : String, cookie : String, nodeConfig : NodeConfig) : Node = apply(Symbol(name), cookie, nodeConfig)
  
  def apply(name : Symbol) =
    new ErlangNode(name, findOrGenerateCookie, NodeConfig(new DefaultThreadPoolFactory, None))
  
  def apply(name : Symbol, cookie : String) =
    new ErlangNode(name, cookie, NodeConfig(new DefaultThreadPoolFactory, None))
  
  def apply(name : Symbol, cookie : String, tpf : ThreadPoolFactory) =
    new ErlangNode(name, cookie, NodeConfig(tpf, None))
  
  def apply(name : Symbol, cookie : String, listener : ClusterListener) =
    new ErlangNode(name, cookie, NodeConfig(new DefaultThreadPoolFactory, Some(listener)))
    
  def apply(name : Symbol, cookie : String, nodeConfig : NodeConfig) =
    new ErlangNode(name, cookie, nodeConfig)
  
  protected def findOrGenerateCookie : String = {
    val homeDir = System.getenv("HOME")
    if (homeDir == null) {
      throw new Exception("No erlang cookie set and cannot read ~/.erlang.cookie.")
    }
    val file = new File(homeDir, ".erlang.cookie")
    if (file.isFile) {
      readFile(file)
    } else {
      val cookie = randomCookie
      writeCookie(file, cookie)
      cookie
    }
  }
  
  protected def randomCookie : String = {
    val ary = new Array[Byte](20)
    random.nextBytes(ary)
    ary.map("%02X" format _).mkString
  }
  
  protected def readFile(file : File) : String = {
    val in = new BufferedReader(new FileReader(file))
    val cookie = in.readLine
    in.close
    cookie
  }
  
  protected def writeCookie(file : File, cookie : String) {
    val out = new FileWriter(file)
    out.write(cookie)
    out.close
  }
}

trait ClusterListener {
  def nodeUp(node : Symbol)
  def nodeDown(node : Symbol)
}

trait ClusterPublisher {
  @volatile var listeners : List[ClusterListener] = Nil
  
  def addListener(listener : ClusterListener) {
    listeners = listener :: listeners
  }
  
  def clearListeners {
    listeners = Nil
  }
  
  def notifyNodeUp(node : Symbol) {
    for (listener <- listeners) {
      listener.nodeUp(node)
    }
  }
  
  def notifyNodeDown(node : Symbol) {
    for (listener <- listeners) {
      listener.nodeDown(node)
    }
  }
}

trait Node extends ClusterListener with ClusterPublisher {
  def name : Symbol
  def cookie : String
  def spawn[T <: Process](implicit mf : Manifest[T]) : Pid
  def spawn[T <: Process](regName : String)(implicit mf : Manifest[T]) : Pid
  def spawn[T <: Process](regName : Symbol)(implicit mf : Manifest[T]) : Pid
  def spawnService[T <: Service[A], A <: Product](args : A)(implicit mf : Manifest[T]) : Pid
  def spawnService[T <: Service[A], A <: Product](regName : String, args : A)(implicit mf : Manifest[T]) : Pid
  def spawnService[T <: Service[A], A <: Product](regName : Symbol, args : A)(implicit mf : Manifest[T]) : Pid
  def spawn(fun : Mailbox => Unit) : Pid
  def spawn(regName : String, fun : Mailbox => Unit) : Pid
  def spawn(regName : Symbol, fun : Mailbox => Unit) : Pid
  def spawnMbox : Mailbox
  def spawnMbox(regName : String) : Mailbox
  def spawnMbox(regName : Symbol) : Mailbox
  def send(to : Pid, msg : Any)
  def send(to : Symbol, msg : Any)
  def send(to : (Symbol,Symbol), from : Pid, msg : Any)
  def call(to : Pid, msg : Any) : Any
  def call(to : Pid, msg : Any, timeout : Long) : Any
  def call(from : Pid, to : Pid, msg : Any) : Any
  def call(from : Pid, to : Pid, msg : Any, timeout : Long) : Any
  def call(to : Symbol, msg : Any) : Any
  def call(to : Symbol, msg : Any, timeout : Long) : Any
  def call(from : Pid, to : Symbol, msg : Any) : Any
  def call(from : Pid, to : Symbol, msg : Any, timeout : Long) : Any
  def call(to : (Symbol,Symbol), msg : Any) : Any
  def call(to : (Symbol,Symbol), msg : Any, timeout : Long) : Any
  def call(from : Pid, to : (Symbol,Symbol), msg : Any) : Any
  def call(from : Pid, to : (Symbol,Symbol), msg : Any, timeout : Long) : Any
  def cast(to : Pid, msg : Any)
  def cast(to : Symbol, msg : Any)
  def cast(to : (Symbol,Symbol), msg : Any)
  def register(regName : String, pid : Pid)
  def register(regName : Symbol, pid : Pid)
  def getNames : Set[Symbol]
  def whereis(name : Symbol) : Option[Pid]
  def ping(node : Symbol, timeout : Long) : Boolean
  def nodes : Set[Symbol]
  def makeRef : Reference
  def isAlive(pid : Pid) : Boolean
  def shutdown
  def timer : HashedWheelTimer
}

class ErlangNode(val name : Symbol, val cookie : String, config : NodeConfig) extends Node 
    with ExitListener 
    with SendListener 
    with LinkListener 
    with ReplyRegistry
    with Instrumented
    with Logging {
  InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory)
  
  val timer = new HashedWheelTimer
  val tickTime = config.tickTime
  val poolFactory = config.poolFactory
  var creation : Int = 0
  val processes = new NonBlockingHashMap[Pid,ProcessLike]
  val registeredNames = new NonBlockingHashMap[Symbol,Pid]
  val channels = AtomicMap.atomicNBHM[Symbol,Channel]
  val links = AtomicMap.atomicNBHM[Channel,NonBlockingHashSet[Link]]
  val pidCount = new AtomicInteger(0)
  val pidSerial = new AtomicInteger(0)
  val executor = poolFactory.createActorPool
  val factory = new PoolFiberFactory(executor)
  val server = new ErlangNodeServer(this,config.typeFactory)
  val localEpmd = Epmd("localhost")
  localEpmd.alive(server.port, splitNodename(name)) match {
    case Some(c) => creation = c
    case None => throw new ErlangNodeException("EPMD alive announcement failed.")
  }
  val referenceCounter = new ReferenceCounter(name, creation)
  val netKernel = spawn[NetKernel]('net_kernel)
  val cluster = spawn[Cluster]('cluster)
  
  def shutdown {
    localEpmd.close
    for ((node,channel) <- channels) {
      channel.close
    }
    for((pid,process) <- processes) {
      process.exit('node_shutdown)
    }
  }
  
  override def finalize {
    shutdown
  }
  
  def spawnMbox : Mailbox = {
    val p = createPid
    val n = this
    val box = new MailboxProcess(new ProcessContext {
      val pid = p
      val referenceCounter = n.referenceCounter
      val node = n
      val fiber = null
      val replyRegistry = n
    })
    box.addExitListener(this)
    box.addSendListener(this)
    box.addLinkListener(this)
    processes.put(p, box)
    box
  }
  
  def spawnMbox(regName : String) : Mailbox = spawnMbox(Symbol(regName))
  def spawnMbox(regName : Symbol) : Mailbox = {
    val mbox = spawnMbox
    register(regName, mbox.self)
    mbox
  }
  
  def createPid : Pid = {
    val id = pidCount.getAndIncrement & 0x7fff
    
    val serial = if (id == 0)
      pidSerial.getAndIncrement & 0x1fff
    else
      pidSerial.get & 0x1fff
    Pid(name,id,serial,creation)
  }
  
  //node external interface
  def spawn(fun : Mailbox => Unit) : Pid = {
    val n = this
    val p = createPid
    val ctx = new ProcessContext {
      val pid = p
      val referenceCounter = n.referenceCounter
      val node = n
      val fiber = factory.create(poolFactory.createBatchExecutor(false))
      val replyRegistry = n
    }
    val process = new FunProcess(fun, ctx)
    process.addExitListener(this)
    process.addSendListener(this)
    process.addLinkListener(this)
    process.fiber.start
    process.start
    p
  }
  
  def spawn(name : String, fun : Mailbox => Unit) : Pid = {
    spawn(Symbol(name), fun)
  }
  
  def spawn(name : Symbol, fun : Mailbox => Unit) : Pid = {
    val n = this
    val p = createPid
    val ctx = new ProcessContext {
      val pid = p
      val referenceCounter = n.referenceCounter
      val node = n
      val fiber = factory.create(poolFactory.createBatchExecutor(name.name,false))
      val replyRegistry = n
    }
    val process = new FunProcess(fun, ctx)
    process.addExitListener(this)
    process.addSendListener(this)
    process.addLinkListener(this)
    process.fiber.start
    process.start
    p
    registeredNames.put(name, p)
    p
  }
  
  def spawn[T <: Process](implicit mf : Manifest[T]) : Pid = {
    val pid = createPid
    createProcess(mf.erasure.asInstanceOf[Class[T]], pid, poolFactory.createBatchExecutor(false))
    pid
  }
  
  def spawn[T <: Process](regName : Symbol)(implicit mf : Manifest[T]) : Pid = {
    val pid = createPid
    val process = createProcess(mf.erasure.asInstanceOf[Class[T]], pid, poolFactory.createBatchExecutor(regName.name, false))
    registeredNames.put(regName, pid)
    pid
  }
  
  def spawn[T <: Process](regName : String)(implicit mf : Manifest[T]) : Pid = {
    spawn(Symbol(regName))(mf)
  }
  
  def spawn[T <: Process](reentrant : Boolean)(implicit mf : Manifest[T]) : Pid = {
    val pid = createPid
    createProcess(mf.erasure.asInstanceOf[Class[T]], pid, poolFactory.createBatchExecutor(reentrant))
    pid
  }
  
  def spawn[T <: Process](regName : Symbol, reentrant : Boolean)(implicit mf : Manifest[T]) : Pid = {
    val pid = createPid
    createProcess(mf.erasure.asInstanceOf[Class[T]], pid, poolFactory.createBatchExecutor(regName.name, reentrant))
    registeredNames.put(regName, pid)
    pid
  }
  
  def spawn[T <: Process](regName : String, reentrant : Boolean)(implicit mf : Manifest[T]) : Pid = {
    spawn[T](Symbol(regName),reentrant)(mf)
  }
  
  def spawnService[T <: Service[A], A <: Product](args : A)(implicit mf : Manifest[T]) : Pid = {
    spawnService[T,A](args, false)(mf)
  }
  
  def spawnService[T <: Service[A], A <: Product](args : A, reentrant : Boolean)(implicit mf : Manifest[T]) : Pid = {
    val pid = createPid
    val process = createService(mf.erasure.asInstanceOf[Class[T]], pid, args, poolFactory.createBatchExecutor(reentrant))
/*    process.init(args)*/
    pid
  }
  
  def spawnService[T <: Service[A], A <: Product](regName : String, args : A)(implicit mf : Manifest[T]) : Pid = {
    spawnService[T,A](regName, args, false)(mf)
  }
  
  def spawnService[T <: Service[A], A <: Product](regName : String, args : A, reentrant : Boolean)(implicit mf : Manifest[T]) : Pid = {
    spawnService[T,A](Symbol(regName), args, reentrant)(mf)
  }
  
  def spawnService[T <: Service[A], A <: Product](regName : Symbol, args : A)(implicit mf : Manifest[T]) : Pid = {
    spawnService[T,A](regName, args, false)(mf)
  }
  
  def spawnService[T <: Service[A], A <: Product](regName : Symbol, args : A, reentrant : Boolean)(implicit mf : Manifest[T]) : Pid = {
    val pid = createPid
    val process = createService(mf.erasure.asInstanceOf[Class[T]], pid, args, poolFactory.createBatchExecutor(regName.name, reentrant))
    registeredNames.put(regName, pid)
    pid
  }
  
  override def nodeDown(node : Symbol) {
    send('cluster, ('nodedown, node))
  }
  
  override def nodeUp(node : Symbol) {
    send('cluster, ('nodeup, node))
  }
  
  protected def createService[A <: Product, T <: Service[A]](clazz : Class[T], p : Pid, a : A, batch : BatchExecutor) : T = {
    val constructor = clazz.getConstructor(classOf[ServiceContext[_]])
    val n = this
    val ctx = new ServiceContext[A] {
      val pid = p
      val referenceCounter = n.referenceCounter
      val node = n
      val fiber = factory.create(batch)
      val replyRegistry = n
      val args = a
    }
    val process = constructor.newInstance(ctx)
    process.addExitListener(this)
    process.addSendListener(this)
    process.addLinkListener(this)
    processes.put(p, process)
    process.fiber.start
    process
  }
  
  protected def createProcess[T <: Process](clazz : Class[T], p : Pid, batch : BatchExecutor) : T = {
    val constructor = clazz.getConstructor(classOf[ProcessContext])
    val n = this
    val ctx = new ProcessContext {
      val pid = p
      val referenceCounter = n.referenceCounter
      val node = n
      val fiber = factory.create(batch)
      val replyRegistry = n
    }
    val process = constructor.newInstance(ctx)
    process.addExitListener(this)
    process.addSendListener(this)
    process.addLinkListener(this)
    processes.put(p, process)
    process.fiber.start
    process
  }
  
  def register(regName : String, pid : Pid) {
    register(Symbol(regName), pid)
  }
  
  def register(regName : Symbol, pid : Pid) {
    registeredNames.put(regName, pid)
  }
  
  def getNames : Set[Symbol] = {
    registeredNames.keySet.toSet.asInstanceOf[Set[Symbol]]
  }
  
  def getConnection(name : Symbol) : Option[Channel] = {
    channels.get(name)
  }
  
  def connectionExists(name : Symbol) : Boolean = {
    channels.containsKey(name)
  }
  
  def tryRegisterConnection(name : Symbol, channel : Channel) : Boolean = {
    null == channels.putIfAbsent(name, channel)
  }
  
  def whereis(regName : Symbol) : Option[Pid] = {
    Option(registeredNames.get(regName))
  }
  
  def ping(node : Symbol, timeout : Long) : Boolean = {
    val mbox = spawnMbox
    val ref = makeRef
    mbox.send(('net_kernel, Symbol(node.name)), mbox.self, ((Symbol("$gen_call"), (mbox.self, ref), ('is_auth, node))))
    val result = mbox.receive(timeout) match {
      case Some((ref, 'yes)) => true
      case m => 
        false
    }
    mbox.exit('normal)
    result
  }
  
  def nodes : Set[Symbol] = {
    channels.keySet.toSet.asInstanceOf[Set[Symbol]]
  }
  
  def deliverLink(link : Link) {
    val from = link.from
    val to = link.to
    log.debug("deliverLink %s -> %s", from, to)
    if (from == to) {
      log.warn("Trying to link a pid to itself: %s", from)
      return
    }
    
    if (isLocal(to)) {
      for (p <- process(to)) {
        p.linkWithoutNotify(from)
      }
    } else {
      getOrConnectAndSend(to.node, LinkMessage(from, to), { channel =>
        val set = links.getOrElseUpdate(channel, new NonBlockingHashSet[Link])
        log.debug("adding remote link %s to channel %s", link, channel)
        set.add(link)
      })
    }
  }
  
  //node internal interface
  def link(from : Pid, to : Pid) {
    log.debug("link %s -> %s", from, to)
    if (from == to) {
      log.warn("Trying to link a pid to itself: %s", from)
      return
    }
    if (!isLocal(from) && !isLocal(to)) {
      log.warn("Trying to link non-local pids: %s -> %s", from, to)
      return
    }
    
    for(p <- process(from)) {
      p.link(to)
    }
    
    for(p <- process(to)) {
      p.link(from)
    }
  }

  // Link two pids without triggering a send of a Link message to the remote.
  def linkWithoutNotify(from : Pid, to : Pid, channel: Channel) {
    log.debug("link w/o notify %s -> %s", from, to)
    if (from == to) {
      log.warn("Trying to link a pid to itself: %s", from)
      return
    }

    if (!isLocal(from) && !isLocal(to)) {
      log.warn("Trying to link non-local pids: %s -> %s", from, to)
      return
    }

    process(from) match {
      case p: Some[Process] =>
        val link = p.get.linkWithoutNotify(to)
        if (!isLocal(from))
          links.getOrElseUpdate(channel, new NonBlockingHashSet[Link]).add(link)
      case None =>
        if (!isLocal(from))
          links.getOrElseUpdate(channel, new NonBlockingHashSet[Link]).add(Link(from, to))
    }

    process(to) match {
      case p: Some[Process] =>
        val link = p.get.linkWithoutNotify(from)
        if (!isLocal(to))
          links.getOrElseUpdate(channel, new NonBlockingHashSet[Link]).add(link)
        
      case None =>
        if (!isLocal(to))
          links.getOrElseUpdate(channel, new NonBlockingHashSet[Link]).add(Link(from, to))
    }
  }
  
  def send(to : Pid, msg : Any) = handleSend(to, msg)
  def send(to : Symbol, msg : Any) = handleSend(to, msg)
  def send(to : (Symbol,Symbol), from : Pid, msg : Any) = handleSend(to, from, msg)
  
  def call(to : Pid, msg : Any) : Any = call(createPid, to, msg)
  def call(to : Pid, msg : Any, timeout : Long) : Any = call(createPid, to, msg, timeout)
  def call(from : Pid, to : Pid, msg : Any) : Any = call(from, to, msg, Long.MaxValue)
  def call(from : Pid, to : Pid, msg : Any, timeout : Long) : Any = {
    val (ref,c) = makeCall(from, msg)
    val channel = makeReplyChannel(from, ref)
    send(to, c)
    waitReply(channel, timeout)
  }
  
  def call(to : Symbol, msg : Any) : Any = call(createPid, to, msg)
  def call(to : Symbol, msg : Any, timeout : Long) : Any = call(createPid, to, msg, timeout)
  def call(from : Pid, to : Symbol, msg : Any) : Any = call(from, to, msg, Long.MaxValue)
  def call(from : Pid, to : Symbol, msg : Any, timeout : Long) : Any = {
    val (ref,c) = makeCall(from, msg)
    val channel = makeReplyChannel(from, ref)
    send(to, c)
    waitReply(channel, timeout)
  }
  
  def call(to : (Symbol,Symbol), msg : Any) = call(createPid, to, msg)
  def call(to : (Symbol,Symbol), msg : Any, timeout : Long) = call(createPid, to, msg, timeout)
  def call(from : Pid, to : (Symbol,Symbol), msg : Any) = call(from, to, msg, Long.MaxValue)
  def call(from : Pid, to : (Symbol,Symbol), msg : Any, timeout : Long) : Any = {
    val (ref,c) = makeCall(from, msg)
    val channel = makeReplyChannel(from, ref)
    send(to, from, c)
    waitReply(channel, timeout)
  }
  
  def cast(to : Pid, msg : Any) = send(to, (Symbol("$gen_cast"), msg))
  def cast(to : Symbol, msg : Any) = send(to, (Symbol("$gen_cast"), msg))
  def cast(to : (Symbol,Symbol), msg : Any) = send(to, createPid, (Symbol("$gen_cast"), msg))
  
  def makeReplyChannel(pid : Pid, ref : Reference) : BlockingQueue[Any] = {
    val queue = new LinkedBlockingQueue[Any]
    registerReplyQueue(pid, ref, queue)
    queue
  }
  
  def waitReply(channel : BlockingQueue[Any], timeout : Long) : Any = {
    channel.poll(timeout, TimeUnit.MILLISECONDS) match {
      case null => ('error, 'timeout)
      case response => response
    }
  }
  
  def makeCall(from : Pid, msg : Any) : (Reference,Any) = {
    val ref = makeRef
    val call = (Symbol("$gen_call"), (from, ref), msg)
    (ref, call)
  }
  
  def handleSend(to : Pid, msg : Any) {
    log.debug("send %s to %s", msg, to)
    if (!tryDeliverReply(to,msg)) {
      if (isLocal(to)) {
        val process = processes.get(to)
        log.debug("send local to %s", process)
        if (process != null) {
          process.handleMessage(msg)
        }
      } else {
        log.debug("send remote to %s", to.node)
        getOrConnectAndSend(to.node, SendMessage(to, msg))
      }
    }
  }
  
  def handleSend(to : Symbol, msg : Any) {
    for (pid <- whereis(to)) {
      handleSend(pid, msg)
    }
  }
  
  def makeRef : Reference = {
    referenceCounter.makeRef
  }
  
  def handleSend(dest : (Symbol,Symbol), from : Pid, msg : Any) {
    val (regName,peer) = dest
    getOrConnectAndSend(peer, RegSend(from, regName, msg))
  }
  
  def handleExit(from : Pid, reason : Any) {
    Option(processes.get(from)) match {
      case Some(pf : Process) =>
        val fiber = pf.fiber
        fiber.dispose
      case _ =>
        Unit
    }
    processes.remove(from)
  }
  
  //this only gets called from a remote link breakage()
  def remoteBreak(link : Link, reason : Any) {
    val from = link.from
    val to = link.to
    for (proc <- process(to)) {
      proc.handleExit(from, reason)
    }
    for (proc <- process(from)) {
      proc.handleExit(to, reason)
    }
  }
  
  //this will only get called from a local link breakage (process exit)
  def break(link : Link, reason : Any) {
    val from = link.from
    val to = link.to
    if (isLocal(to)) {
      for (proc <- process(to)) {
/*        println("exiting " + proc)*/
        proc.handleExit(from, reason)
      }
    } else {
      getOrConnectAndSend(to.node, ExitMessage(from,to,reason))
    }
  }
  
  def process(pid : Pid) : Option[ProcessLike] = {
    Option(processes.get(pid))
  }
  
  def unlink(from : Pid, to : Pid) {
    for (p <- process(from)) {
      p.unlink(to)
    }
    
    for (p <- process(to)) {
      p.unlink(from)
    }
  }
  
  def isLocal(pid : Pid) : Boolean = {
    pid.node == name //&& pid.creation == creation
  }
  
  def disconnected(peer : Symbol, channel: Channel) {
    if (channels.containsKey(peer)) {
      channels.remove(peer)
      //must break all links here
      if (links.contains(channel)) {
        val setOption = links.remove(channel)
        for (set <- setOption; link <- set) {
          log.debug("breaking %s", link)
          remoteBreak(link, 'noconnection)
        }
      }
    }
  }
  
  def getOrConnectAndSend(peer : Symbol, msg : Any, afterHandshake : Channel => Unit = { channel => Unit }) {
    channels.get(peer) match {
      case Some(channel) =>
        channel.write(msg)
        afterHandshake(channel)
      case None =>
        connectAndSend(peer, Some(msg), afterHandshake)
    }
  }
  
  def connectAndSend(peer : Symbol, msg : Option[Any] = None, afterHandshake : Channel => Unit = {_ => Unit }) {
    try {
      val hostname = splitHostname(peer).getOrElse(throw new ErlangNodeException("Cannot resolve peer with no hostname: " + peer.name))
      val peerName = splitNodename(peer)
      val port = Epmd(hostname).lookupPort(peerName).getOrElse(throw new ErlangNodeException("Cannot lookup peer: " + peer.name))
      val channel = channels.getOrElseUpdate(peer, {
        val client = new ErlangNodeClient(this, peer, hostname, port, config.typeFactory, afterHandshake)
        client.channel
      })
      for (m <- msg) {
        channel.write(m)
      }
      afterHandshake(channel)
    } catch {
      case e : Exception =>
        log.debug(e, "Exception in connect and send.")
    }
  }
  
  def posthandshake : (Symbol,ChannelPipeline) => Unit = {
    { (peer : Symbol, p : ChannelPipeline) =>
      p.addFirst("packetCounter", new PacketCounter("stream-" + peer.name))
      if (p.get("encoderFramer") != null)
        p.addAfter("encoderFramer", "framedCounter", new PacketCounter("framed-" + peer.name))
      if (p.get("erlangEncoder") != null)
        p.addAfter("erlangEncoder", "erlangCounter", new PacketCounter("erlang-" + peer.name))
      p.addAfter("erlangCounter", "failureDetector", new FailureDetectionHandler(name, new SystemClock, tickTime, timer))
    }
  }
  
  def splitNodename(peer : Symbol) : String = {
    val parts = peer.name.split('@')
    if (parts.length < 2) {
      peer.name
    } else {
      parts(0)
    }
  }
  
  def splitHostname(peer : Symbol) : Option[String] = {
    val parts = peer.name.split('@')
    if (parts.length < 2) {
      None
    } else {
      Some(parts(1))
    }
  }
  
  def isAlive(pid : Pid) : Boolean = {
    process(pid) match {
      case Some(_) => true
      case None => false
    }
  }
}

class ErlangNodeException(msg : String) extends Exception(msg)
