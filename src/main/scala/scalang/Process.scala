package scalang

import org.jetlang.fibers._

abstract class Process {
  /**
   * Clients of this class must subclass the delivery method in order to
   * handle the delivery of any external erlang messages.
   */
  def delivery(msg : ETerm) : Unit
  
  def send(pid : Pid, msg : ETerm) {
    
  }
  
  def send(name : String, msg : ETerm) {
    
  }
  
  def send(name : String, node : String, msg : ETerm) {
    
  }
  
  def exit {
    
  }
}