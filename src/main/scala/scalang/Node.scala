package scalang

import java.util.concurrent.atomic._
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.{netty => netty}
import netty.channel._
import netty.bootstrap._
import socket.nio.NioServerSocketChannelFactory
import java.util.concurrent._
import scalang.util.Log
import scalang.node._
import org.cliffc.high_scale_lib.NonBlockingHashMap
import org.jetlang._
import core._
import java.io._
import fibers.PoolFiberFactory
import core.BatchExecutorImpl
import scala.collection.JavaConversions._
import scalang.epmd._
import scalang.util._
import java.security.SecureRandom

object Node {
  val random = SecureRandom.getInstance("SHA1PRNG")
  
  def apply(name : String) = apply(Symbol(name))
  def apply(name : String, cookie : String) = apply(Symbol(name), cookie)
  def apply(name : String, cookie : Stirng, tpf : ThreadPoolFactory) = apply(Symbol(name), cookie, tpf)
  def apply(name : String, cookie : String, listener : ClusterListener) = apply(Symbol(name), cookie, listener)
  def apply(name : String, cookie : String, nodeConfig : NodeConfig) = apply(Symbol(name), cookie, nodeConfig)
  
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
    ary.map((x : Byte) => x.toHexString ).mkString("")
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
  def spawnMbox : Mailbox
  def spawnMbox(regName : String) : Mailbox
  def spawnMbox(regName : Symbol) : Mailbox
  def register(regName : String, pid : Pid)
  def register(regName : Symbol, pid : Pid)
  def getNames : Set[Symbol]
  def whereis(name : Symbol) : Option[Pid]
  def ping(node : Symbol, timeout : Long) : Boolean
  def nodes : Set[Symbol]
  def makeRef : Reference
  def isAlive(pid : Pid) : Boolean
  def shutdown
}

class ErlangNode(val name : Symbol, val cookie : String, config : NodeConfig) extends Node with Log with ExitListener with SendListener with LinkListener {
  val poolFactory = config.poolFactory
  var creation : Int = 0
  val processes = new NonBlockingHashMap[Pid,ProcessLike]
  val registeredNames = new NonBlockingHashMap[Symbol,Pid]
  val channels = new NonBlockingHashMap[Symbol,Channel]
  val pidCount = new AtomicInteger(0)
  val pidSerial = new AtomicInteger(0)
  val executor = poolFactory.createActorPool
  val factory = new PoolFiberFactory(executor)
  val server = new ErlangNodeServer(this)
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
    val box = new Mailbox(new ProcessContext {
      val pid = p
      val referenceCounter = n.referenceCounter
      val node = n
      val fiber = null
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
    val id = pidCount.getAndIncrement
    val serial = pidSerial.getAndIncrement
    Pid(name,id,serial,creation)
  }
  
  //node external interface
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
  
  override def nodeDown(node : Symbol) {
    send('cluster, ('nodedown, node))
  }
  
  override def nodeUp(node : Symbol) {
    send('cluster, ('nodeup, node))
  }
  
  protected def createProcess[T <: Process](clazz : Class[T], p : Pid, batch : BatchExecutor) : Process = {
    val constructor = clazz.getConstructor(classOf[ProcessContext])
    val n = this
    val ctx = new ProcessContext {
      val pid = p
      val referenceCounter = n.referenceCounter
      val node = n
      val fiber = factory.create(batch)
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
  
  def registerConnection(name : Symbol, channel : Channel) {
    channels.put(name, channel)
  }
  
  def whereis(regName : Symbol) : Option[Pid] = {
    Option(registeredNames.get(regName))
  }
  
  def ping(node : Symbol, timeout : Long) : Boolean = {
    val mbox = spawnMbox
    val ref = makeRef
    mbox.send('net_kernel, (Symbol("$gen_call"), (mbox.self, ref), ('is_auth, name)))
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
  
  def deliverLink(from : Pid, to : Pid) {
    if (from == to) {
      warn("Trying to link a pid to itself: " + from)
      return
    }
    
    if (isLocal(to)) {
      for (p <- process(to)) {
        p.linkWithoutNotify(from)
      }
    } else {
      getOrConnectAndSend(to.node, LinkMessage(from, to))
    }
  }
  
  //node internal interface
  def link(from : Pid, to : Pid) {
    if (from == to) {
      warn("Trying to link a pid to itself: " + from)
      return
    }
    if (!isLocal(from) && !isLocal(to)) {
      warn("Trying to link non-local pids: " + from + " -> " + to)
      return
    }
    
    for(p <- process(from)) {
      p.link(to)
    }
    
    for(p <- process(to)) {
      p.link(from)
    }
  }
  
  def send(to : Pid, msg : Any) = handleSend(to, msg)
  def send(to : Symbol, msg : Any) = handleSend(to, msg)
  def send(to : (Symbol,Symbol), from : Pid, msg : Any) = handleSend(to, from, msg)
  
  def handleSend(to : Pid, msg : Any) {
    if (isLocal(to)) {
      val process = processes.get(to)
      if (process != null) {
        process.handleMessage(msg)
      }
    } else {
      getOrConnectAndSend(to.node, SendMessage(to, msg))
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
  
  def break(from : Pid, to : Pid, reason : Any) {
    if (isLocal(to)) {
      for (proc <- process(to)) {
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
    pid.node == name && pid.creation == creation
  }
  
  def disconnected(peer : Symbol) {
    channels.remove(peer)
  }
  
  def getOrConnectAndSend(peer : Symbol, msg : Any) {
    Option(channels.get(peer)) match {
      case Some(channel) => channel.write(msg)
      case None => connectAndSend(peer, Some(msg))
    }
  }
  
  def connectAndSend(peer : Symbol, msg : Option[Any] = None) {
    val hostname = splitHostname(peer).getOrElse(throw new ErlangNodeException("Cannot resolve peer with no hostname: " + peer.name))
    val peerName = splitNodename(peer)
    val port = Epmd(hostname).lookupPort(peerName).getOrElse(throw new ErlangNodeException("Cannot lookup peer: " + peer.name))
    val client = new ErlangNodeClient(this, hostname, port, msg)
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