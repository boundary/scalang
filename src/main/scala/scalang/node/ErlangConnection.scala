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
import epmd.Epmd
import com.codahale.logula.Logging
import org.jboss.netty
import netty.channel._
import java.util.concurrent.atomic.AtomicReference
import scalang.util.Hostname
import org.cliffc.high_scale_lib._
import java.util.ArrayDeque
import scala.collection.immutable.List
import overlock.lock.Lock
import scala.collection.JavaConversions._

/**
  * ErlangConnection is a delegate class for dealing with the inbound and outbound connection handling for a single peer. We
  * delegate to this class in order to tighten down the surface area for races in the connection code.  There should only be
  * a single connection active for a given peer node, which is enforced in this class.
  *
  * Inbound connections are difficult to deal with, since we will not know the peer name until the handshake is underway, and
  * this class isn't instantiated until a peer is known.  This class can be in the following states:
  * 1) No connection attempted or underway.
  * 2) Connection with handshake in progress.
  * 3) Active connection.
  * 4) Broken connection that we don't know about yet.
  * 
  * Connection attempts initiated by our side should be easy enough to deal with by making connection opens the responsibility
  * of this class.  We will initiate a connection if and only if there is no connection currently registered.  Messages will get
  * queued internally to deal with the thundering herd problem, and then drained once a connection finally opens.
  *
  * Incoming connection attempts are problematic because we cannot control them.  The connection will attempt to register itself
  * with the connection, via the node object.  This is where the state comes into play.  If a connection comes in
  */
class ErlangConnection(node : ErlangNode, peer : Symbol, config : NodeConfig) extends Logging {
  
  val links = new NonBlockingHashSet[Link]
  val monitors = new NonBlockingHashSet[Monitor]
  val channelRef = new AtomicReference[Channel]
  @volatile var handshakeFuture : ChannelFuture = null
  val queue = new ArrayDeque[(ChannelFuture,Any)]
  @volatile var drained = false
  val queueLock = new Lock
  
  def write(msg : Any) : ChannelFuture = {
    queueLock.readLock {
      if (!drained) {
        val future = Channels.future(channelRef.get)
        queue.offer((future,msg))
        future
      } else {
        channelRef.get.write(msg)
      }
    }
  }

  protected def breakAll {
    for (link <- links) {
      link.localBreak('noconnection)
    }
    links.clear
    for (monitor <- monitors) {
      monitor.monitorExit('noconnection)
    }
    monitors.clear
  }
  
  def close {
    if (channelRef.get != null) {
      channelRef.get.close
    }
  }
  
  def connect {
    val hostname = Hostname.splitHostname(peer).getOrElse(throw new ErlangNodeException("Cannot resolve peer with no hostname: " + peer.name))
    val peerName = Hostname.splitNodename(peer)
    val port = Epmd(hostname).lookupPort(peerName).getOrElse(throw new ErlangNodeException("Cannot lookup peer: " + peer.name))
    val client = new ErlangNodeClient(node, peer, hostname, port, None, 
      config.typeFactory,
      config.typeEncoder)
  }
  
  def disconnected(channel : Channel) {
    if (channelRef.compareAndSet(channel, null)) {
      breakAll
    }
  }
  
  // returns true if we succeed. this is sketch.
  def forceConnection(channel : Channel) : Boolean = {
    var oldChannel = channelRef.get
    oldChannel.close
    drained = false
    channelRef.compareAndSet(oldChannel, channel)
  }
  
  // this is safe, will not clobber connections. 
  def connectRequested(future : ChannelFuture, channel : Channel) : Symbol = {
    val oldChannel = channelRef.get
    if (oldChannel == null && channelRef.compareAndSet(oldChannel, channel)) {
      handshakeFuture = future
      drained = false
      handshakeFuture.addListener(new ChannelFutureListener {
        def operationComplete(future : ChannelFuture) {
          if (future.isSuccess) {
            handshakeFuture = null
            drainQueue
          } else {
            // handle disconn?
          }
        }
      })
      'ok
    } else if (handshakeFuture.isDone) {
      'alive
    } else {
      'ok_simultaneous
    }
  }
  
  //monitors
  def addLink(link : Link) {
    links.add(link)
  }
  
  def break(link : Link, reason : Any) {
    
  }
  
  def unlink(link : Link) {
    links.remove(link)
  }
  
  //monitors
  def addMonitor(monitor : Monitor) {
    monitors.add(monitor)
  }
  
  def break(monitor : Monitor, reason : Any) {
    
  }
  
  def unmonitor(monitor : Monitor) {
    monitors.remove(monitor)
  }
  
  protected def drainQueue {
    queueLock.writeLock {
      val p = channelRef.get.getPipeline
      val keys = p.toMap.keySet
      for (name <- List("handshakeFramer", "handshakeDecoder", "handshakeEncoder", "handshakeHandler"); if keys.contains(name)) {
        p.remove(name)
      }
      p.addFirst("packetCounter", new PacketCounter("stream-" + peer.name))
      if (p.get("encoderFramer") != null)
        p.addAfter("encoderFramer", "framedCounter", new PacketCounter("framed-" + peer.name))
      if (p.get("erlangEncoder") != null)
        p.addAfter("erlangEncoder", "erlangCounter", new PacketCounter("erlang-" + peer.name))
      p.addAfter("erlangCounter", "failureDetector", new FailureDetectionHandler(peer, new SystemClock, config.tickTime, node.timer))
      
      for ((future,msg) <- queue) {
        channelRef.get.write(msg)
        future.setSuccess
      }
      queue.clear
      drained = true
    }
  }
}