package scalang

import org.specs._
import java.lang.{Process => JProc}

class ServiceSpec extends Specification {
  "Service" should {
    val cookie = "test"
    var epmd : JProc = null
    var node : ErlangNode = null
    doBefore {
      epmd = EpmdCmd()
    }
    
    doAfter {
      epmd.destroy
      epmd.waitFor
      node.shutdown
    }
    
    "deliver casts" in {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawn[CastNoopService]
      node.send(service, (Symbol("$gen_cast"),'blah))
      node.isAlive(service) must ==(true)
    }
    
    "deliver calls" in {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawn[CallEchoService]
      val mbox = node.spawnMbox
      val ref = node.makeRef
      node.send(service, (Symbol("$gen_call"), (mbox.self, ref), 'blah))
      mbox.receive must ==((ref,'blah))
    }
    
    "respond to pings" in {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawn[CastNoopService]
      val mbox = node.spawnMbox
      val ref = node.makeRef
      node.send(service, ('ping, mbox.self, ref))
      mbox.receive must ==(('pong, ref))
    }
    
    "call and response" in {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawn[CallAndReceiveService]
      val mbox = node.spawnMbox
      node.send(service, mbox.self)
      val (Symbol("$gen_call"), (_, ref : Reference), req) = mbox.receive
      req must ==("blah")
      node.send(service, (ref, "barf"))
      mbox.receive must ==("barf")
    }
  }
}

class CallAndReceiveService(ctx : ProcessContext) extends Service(ctx) {
  override def handleCast(msg : Any) {
    throw new Exception
  }
  
  override def handleCall(from : Pid, msg : Any) : Any = {
    throw new Exception
  }
  
  override def handleInfo(msg : Any) {
    val pid = msg.asInstanceOf[Pid]
    val response = call(pid, "blah")
    pid ! response
  }
}

class CastNoopService(ctx : ProcessContext) extends Service(ctx) {
  override def handleCast(msg : Any) {
    println("cast received " + msg)
  }
  
  override def handleCall(from : Pid, msg : Any) : Any = {
    throw new Exception
  }
  
  override def handleInfo(msg : Any) {
    throw new Exception
  }
}

class CallEchoService(ctx : ProcessContext) extends Service(ctx) {
  override def handleCast(msg : Any) {
    throw new Exception
  }
  
  override def handleCall(from : Pid, msg : Any) : Any = {
    msg
  }
  
  override def handleInfo(msg : Any) {
    throw new Exception
  }
}