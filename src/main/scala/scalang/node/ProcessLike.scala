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
package scalang.node

import scalang._
import org.cliffc.high_scale_lib.NonBlockingHashSet
import scala.collection.JavaConversions._

trait ProcessLike extends ExitListenable with SendListenable with LinkListenable {
  @volatile var state = 'alive
  def self : Pid
  
  def referenceCounter : ReferenceCounter
  
  def handleMessage(msg : Any)
  
  def send(pid : Pid, msg : Any) = notifySend(pid,msg)
  def send(name : Symbol, msg : Any) = notifySend(name,msg)
  def send(dest : (Symbol,Symbol), from : Pid, msg : Any) = notifySend(dest,from,msg)
  
  def handleExit(from : Pid, reason : Any) {
    exit(reason)
  }
  
  def makeRef : Reference = {
    referenceCounter.makeRef
  }
  
  def exit(reason : Any) {
    if (state != 'alive) return
    state = 'dead
    for (link <- links) {
      link.break(reason)
    }
    for(e <- exitListeners) {
      e.handleExit(self, reason)
    }
  }

/*  def spawn[T <: Process](implicit mf : Manifest[T]) : Pid = node.spawn[T](mf)
  def spawn[T <: Process](regName : String)(implicit mf : Manifest[T]) : Pid = node.spawn[T](regName)(mf)
  def spawn[T <: Process](regName : Symbol)(implicit mf : Manifest[T]) : Pid = node.spawn[T](regName)(mf)
  
  def spawnLink[T <: Process](implicit mf : Manifest[T]) : Pid = {
    val pid = node.spawn[T](mf)
    link(pid)
    pid
  }
  
  def spawnLink[T <: Process](regName : String)(implicit mf : Manifest[T]) : Pid = {
    val pid = node.spawn[T](regName)(mf)
    link(pid)
    pid
  }
  
  def spawnLink[T <: Process](regName : Symbol)(implicit mf : Manifest[T]) : Pid = {
    val pid = node.spawn[T](regName)(mf)
    link(pid)
    pid
  }*/

  val links = new NonBlockingHashSet[Link]
  
  def link(to : Pid) {
    linkWithoutNotify(to)
    for (listener <- linkListeners) {
      listener.deliverLink(Link(self, to))
    }
  }
  
  def linkWithoutNotify(to : Pid) : Link = {
    val l = Link(self, to)
    for (listener <- linkListeners) {
      l.addLinkListener(listener)
    }
    links.add(l)
    l
  }
  
  def unlink(to : Pid) {
    links.remove(to)
  }
}