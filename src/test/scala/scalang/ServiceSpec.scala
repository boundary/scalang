package scalang

import org.specs._
import java.lang.{Process => JProc}

class ServiceSpec extends Specification {
  "Service" should {
    val cookie = "test"
    var epmd : JProc = null
    doBefore {
      epmd = EpmdCmd()
    }
    
    doAfter {
      epmd.destroy
      epmd.waitFor
    }
    
    "deliver casts" in {
      val node = new ErlangNode(Symbol("test@localhost"), cookie)
      val service = node.spawn[CastNoopService]
      node.send(service, (Symbol("$gen_cast"),'blah))
      node.isAlive(service) must ==(true)
    }
    
    "deliver calls" in {
      val node = new ErlangNode(Symbol("test@localhost"), cookie)
      val service = node.spawn[CallEchoService]
      val mbox = node.spawnMbox
      val ref = node.makeRef
      node.send(service, (Symbol("$gen_call"), (mbox.self, ref), 'blah))
      mbox.receive must ==((ref,'blah))
    }
    
    "respond to pings" in {
      val node = new ErlangNode(Symbol("test@localhost"), cookie)
      val service = node.spawn[CastNoopService]
      val mbox = node.spawnMbox
      val ref = node.makeRef
      node.send(service, ('ping, mbox.self, ref))
      mbox.receive must ==(('pong, ref))
    }
  }
}

class CastNoopService(ctx : ProcessContext) extends Service(ctx) {
  override def handleCast(msg : Any) {
    println("cast received " + msg)
  }
}

class CallEchoService(ctx : ProcessContext) extends Service(ctx) {
  override def handleCall(from : Pid, msg : Any) : Any = {
    msg
  }
}