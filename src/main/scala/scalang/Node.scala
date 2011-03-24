package scalang

import java.util.concurrent.atomic._
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.{netty => netty}
import netty.channel._
import netty.bootstrap._
import socket.nio.NioServerSocketChannelFactory

object Node {
  def apply() {
    
  }
}

trait Node {
  def name : Symbol
  def cookie : String
  def createProcess(process : Process) : Pid
  def createProcess(regName : String, process : Process) : Pid
  def registerName(regName : String, pid : Pid)
  def getNames : Set[String]
  def whereis(name : Symbol) : Option[Pid]
  def ping(node : String, timeout : Long)
  def nodes : Set[String]
}

class ErlangNode(val name : Symbol, val host : String, val cookie : String) extends Node {
  
  def createProcess(process : Process) : Pid = {
    null
  }
  
  def createProcess(regName : String, process : Process) : Pid = {
    null
  }
  
  def registerName(regName : String, pid : Pid) {
    
  }
  
  def getNames : Set[String] = {
    null
  }
  
  def whereis(regName : Symbol) : Option[Pid] = {
    None
  }
  
  def ping(node : String, timeout : Long) {
    
  }
  
  def nodes : Set[String] = {
    null
  }
}

