package scalang

import org.specs._
import java.lang.{Process => JProc}

class ServiceSpec extends SpecificationWithJUnit {
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
      val service = node.spawnService[CastNoopService,NoArgs](NoArgs)
      node.send(service, (Symbol("$gen_cast"),'blah))
      node.isAlive(service) must ==(true)
    }

    "deliver calls" in {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[CallEchoService,NoArgs](NoArgs)
      val mbox = node.spawnMbox
      val ref = node.makeRef
      node.send(service, (Symbol("$gen_call"), (mbox.self, ref), 'blah))
      mbox.receive must ==((ref,'blah))
    }

    "respond to pings" in {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[CastNoopService,NoArgs](NoArgs)
      val mbox = node.spawnMbox
      val ref = node.makeRef
      node.send(service, ('ping, mbox.self, ref))
      mbox.receive must ==(('pong, ref))
    }

    "call and response" in {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[CallAndReceiveService,NoArgs](NoArgs)
      val mbox = node.spawnMbox
      node.send(service, mbox.self)
      val (Symbol("$gen_call"), (_, ref : Reference), req) = mbox.receive
      req must ==("blah")
      node.send(service, (ref, "barf"))
      mbox.receive must ==("barf")
    }
    
    "trap exits" in {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[TrapExitService,NoArgs](NoArgs)
      val mbox = node.spawnMbox
      mbox.link(service)
      mbox.exit('terminate)
      Thread.sleep(1000)
      node.isAlive(service) must ==(true)
    }
  }
}

class TrapExitService(ctx : ServiceContext[NoArgs]) extends Service(ctx) {
  
  override def trapExit(from : Pid, reason : Any) {
    println("herp " + reason)
  }
  
}

class CallAndReceiveService(ctx : ServiceContext[NoArgs]) extends Service(ctx) {

  override def handleCast(msg : Any) {
    throw new Exception
  }

  override def handleCall(tag : (Pid, Reference), msg : Any) : Any = {
    throw new Exception
  }

  override def handleInfo(msg : Any) {
    val pid = msg.asInstanceOf[Pid]
    val response = call(pid, "blah")
    pid ! response
  }
}

class CastNoopService(ctx : ServiceContext[NoArgs]) extends Service(ctx) {
  override def handleCast(msg : Any) {
    println("cast received " + msg)
  }

  override def handleCall(tag : (Pid, Reference), msg : Any) : Any = {
    throw new Exception
  }

  override def handleInfo(msg : Any) {
    throw new Exception
  }
}

class CallEchoService(ctx : ServiceContext[NoArgs]) extends Service(ctx) {

  override def handleCast(msg : Any) {
    throw new Exception
  }

  override def handleCall(tag : (Pid, Reference), msg : Any) : Any = {
    msg
  }

  override def handleInfo(msg : Any) {
    throw new Exception
  }
}
