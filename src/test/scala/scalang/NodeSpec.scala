package scalang

import org.specs._
import org.specs.runner._
import scalang.node._
import java.lang.{Process => JProc}
import java.io._
import scala.collection.JavaConversions._

class NodeSpec extends SpecificationWithJUnit {
  "Node" should {
    var epmd : JProc = null
    var erl : JProc = null
    var node : ErlangNode = null
    doBefore {
      epmd = EpmdCmd()
    }

    doAfter {
      epmd.destroy
      epmd.waitFor
      if (node != null) { node.shutdown }
      if (erl != null) {
        erl.destroy
        erl.waitFor
      }
    }

    val cookie = "test"

    "get connections from a remote node" in {
      node = Node(Symbol("test@localhost"), cookie)
      erl = ErlangVM("tmp@localhost", cookie, Some("io:format(\"~p~n\", [net_kernel:connect_node('test@localhost')])."))
      val read = new BufferedReader(new InputStreamReader(erl.getInputStream))
      read.readLine
      node.channels.keySet.toSet must contain(Symbol("tmp@localhost"))
    }

    "connect to a remote node" in {
      node = Node(Symbol("scala@localhost"), cookie)
      erl = Escript("receive_connection.escript")
      ReadLine(erl) //ready
      val pid = node.createPid
      node.connectAndSend(Symbol("test@localhost"), None)
      val result = ReadLine(erl)
      result must ==("scala@localhost")
      node.channels.keySet.toSet must contain(Symbol("test@localhost"))
    }

    "accept pings" in {
      node = Node(Symbol("scala@localhost"), cookie)
      erl = ErlangVM("tmp@localhost", cookie, Some("io:format(\"~p~n\", [net_adm:ping('scala@localhost')])."))
      val result = ReadLine(erl)
      result must ==("pong")
      node.channels.keySet.toSet must contain(Symbol("tmp@localhost"))
    }

    "send pings" in {
      node = Node(Symbol("scala@localhost"), cookie)
      erl = Escript("receive_connection.escript")
      ReadLine(erl)
      node.ping(Symbol("test@localhost"), 1000) must ==(true)
    }

    "invalid pings should fail" in {
      node = Node(Symbol("scala@localhost"), cookie)
      node.ping(Symbol("taco_truck@localhost"), 1000) must ==(false)
    }

    "send local regname" in {
      node = Node(Symbol("scala@localhost"), cookie)
      val echoPid = node.spawn[EchoProcess]('echo)
      val mbox = node.spawnMbox
      node.send('echo, (mbox.self, 'blah))
      mbox.receive must ==('blah)
    }

    "send remote regname" in {
      node = Node(Symbol("scala@localhost"), cookie)
      erl = Escript("echo.escript")
      ReadLine(erl)
      val mbox = node.spawnMbox
      node.send(('echo, Symbol("test@localhost")), mbox.self, (mbox.self, 'blah))
      mbox.receive must ==('blah)
    }

    "receive remove regname" in {
      node = Node(Symbol("scala@localhost"), cookie)
      erl = Escript("echo.escript")
      ReadLine(erl)
      val mbox = node.spawnMbox("mbox")
      node.send(('echo, Symbol("test@localhost")), mbox.self, (('mbox, Symbol("scala@localhost")), 'blah))
      mbox.receive must ==('blah)
    }

    "remove processes on exit" in {
      node = Node(Symbol("scala@localhost"), cookie)
      val pid = node.spawn[FailProcess]
      node.processes.get(pid) must beLike { case f : ProcessLauncher[_] => true }
      node.handleSend(pid, 'bah)
      Thread.sleep(100)
      Option(node.processes.get(pid)) must beNone
    }

    "deliver local breakages" in {
      node = Node(Symbol("scala@localhost"), cookie)
      val linkProc = node.spawn[LinkProcess]
      val failProc = node.spawn[FailProcess]
      val mbox = node.spawnMbox
      node.send(linkProc, (failProc, mbox.self))
      Thread.sleep(100)
      mbox.receive must ==('ok)
      node.send(failProc, 'fail)
      Thread.sleep(100)
      node.isAlive(failProc) must ==(false)
      node.isAlive(linkProc) must ==(false)
    }

    "deliver remote breakages" in {
      node = Node(Symbol("scala@localhost"), cookie)
      val mbox = node.spawnMbox('mbox)
      val scala = node.spawnMbox('scala)
      erl = Escript("link_delivery.escript")
      val remotePid = mbox.receive.asInstanceOf[Pid]
      mbox.link(remotePid)
      mbox.exit('blah)
      scala.receive must ==('blah)
    }

    "deliver local breakages" in {
      node = Node(Symbol("scala@localhost"), cookie)
      val mbox = node.spawnMbox('mbox)
      erl = Escript("link_delivery.escript")
      val remotePid = mbox.receive.asInstanceOf[Pid]
      mbox.link(remotePid)
      node.send(remotePid, 'blah)
      Thread.sleep(200)
      node.isAlive(mbox.self) must ==(false)
    }

    "deliver breaks on channel disconnect" in {
       println("discon")
       node = Node(Symbol("scala@localhost"), cookie)
       val mbox = node.spawnMbox('mbox)
       erl = Escript("link_delivery.escript")
       val remotePid = mbox.receive.asInstanceOf[Pid]
       mbox.link(remotePid)
       erl.destroy
       erl.waitFor
       Thread.sleep(100)
       node.isAlive(mbox.self) must ==(false)
     }

     "deliver local monitor exits" in {
       node = Node(Symbol("scala@localhost"), cookie)
       val monitorProc = node.spawn[MonitorProcess]
       val failProc = node.spawn[FailProcess]
       val mbox = node.spawnMbox
       node.send(monitorProc, (failProc, mbox.self))
       Thread.sleep(100)
       mbox.receive must ==('ok)
       node.send(failProc, 'fail)
       Thread.sleep(100)
       mbox.receive must ==('monitor_exit)
       node.isAlive(failProc) must ==(false)
       node.isAlive(monitorProc) must ==(true)
     }

     "deliver remote monitor exits" in {
       node = Node(Symbol("scala@localhost"), cookie)
       val mbox = node.spawnMbox('mbox)
       val scala = node.spawnMbox('scala)
       erl = Escript("monitor.escript")
       val remotePid = mbox.receive.asInstanceOf[Pid]

       // tell remote node to monitor our mbox.
       node.send(remotePid, ('monitor, mbox.self))
       val remoteRef = scala.receive.asInstanceOf[Reference]

       // kill our mbox and await notification from remote node.
       mbox.exit('blah)
       scala.receive must ==(('down, 'blah))
     }

     "don't deliver remote monitor exit after demonitor" in {
       node = Node(Symbol("scala@localhost"), cookie)
       val mbox = node.spawnMbox('mbox)
       val scala = node.spawnMbox('scala)
       erl = Escript("monitor.escript")
       val remotePid = mbox.receive.asInstanceOf[Pid]

       // tell remote node to monitor our mbox.
       node.send(remotePid, ('monitor, mbox.self))
       val remoteRef = scala.receive.asInstanceOf[Reference]

       // tell remote node to stop monitoring our mbox.
       node.send(remotePid, ('demonitor, remoteRef))
       scala.receive must ==(('demonitor, remoteRef))

       // kill our mbox and expect no notification from remote node.
       mbox.exit('blah)
       scala.receive(100) must ==(None)
     }

     "receive remote monitor exits" in {
       node = Node(Symbol("scala@localhost"), cookie)
       val monitorProc = node.spawn[MonitorProcess]
       val mbox = node.spawnMbox('mbox)
       val scala = node.spawn[MonitorProcess]('scala)
       erl = Escript("monitor.escript")
       val remotePid = mbox.receive.asInstanceOf[Pid]

       node.send(monitorProc, (remotePid, mbox.self))
       Thread.sleep(100)
       mbox.receive must ==('ok)
       node.send(monitorProc, ('exit, 'blah))
       Thread.sleep(100)
       mbox.receive must ==('monitor_exit)
       node.isAlive(monitorProc) must ==(true)
   }

     "deliver local monitor exit for unregistered process" in {
       node = Node(Symbol("scala@localhost"), cookie)
       val mbox = node.spawnMbox
       val ref = mbox.monitor('foo)
       Thread.sleep(100)
       mbox.receive must ==('DOWN, ref, 'process, 'foo, 'noproc)
     }

     "deliver remote monitor exit for unregistered process" in {
       node = Node(Symbol("scala@localhost"), cookie)
       val mbox = node.spawnMbox('mbox)
       val scala = node.spawnMbox('scala)
       erl = Escript("monitor.escript")
       val remotePid = mbox.receive.asInstanceOf[Pid]
       node.send(remotePid, ('monitor, 'foo))
       val remoteRef = scala.receive.asInstanceOf[Reference]
       scala.receive must ==(('down, 'noproc))
     }

  }
}
