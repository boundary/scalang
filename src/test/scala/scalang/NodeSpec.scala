package scalang

import org.specs._
import scalang.node._
import java.lang.{Process => JProc}
import java.io._
import scala.collection.JavaConversions._

class NodeSpec extends Specification {
  "Node" should {
    var epmd : JProc = null
    var erl : JProc = null
    doBefore {
      epmd = EpmdCmd()
    }
    
    doAfter {
      epmd.destroy
      epmd.waitFor
      if (erl != null) {
        erl.destroy
        erl.waitFor
      }
    }
    
    val cookie = "test"
    
    "get connections from a remote node" in {
      val node = new ErlangNode(Symbol("test@localhost"), cookie)
      erl = ErlangVM("tmp@localhost", cookie, Some("io:format(\"~p~n\", [net_kernel:connect_node('test@localhost')])."))
      val read = new BufferedReader(new InputStreamReader(erl.getInputStream))
      println(read.readLine)
      node.channels.keySet.toSet must contain(Symbol("tmp@localhost"))
    }
    
    "connect to a remote node" in {
      val node = new ErlangNode(Symbol("scala@localhost"), cookie)
      erl = Escript("receive_connection.escript")
      ReadLine(erl) //ready
      val pid = node.createPid
      node.connectAndSend(Symbol("test@localhost"), None)
      val result = ReadLine(erl)
      result must ==("scala@localhost")
      node.channels.keySet.toSet must contain(Symbol("test@localhost"))
    }
    
    "accept pings" in {
      val node = new ErlangNode(Symbol("scala@localhost"), cookie)
      erl = ErlangVM("tmp@localhost", cookie, Some("io:format(\"~p~n\", [net_adm:ping('scala@localhost')])."))
      val result = ReadLine(erl)
      result must ==("pong")
      node.channels.keySet.toSet must contain(Symbol("tmp@localhost"))
    }
    
    "send pings" in {
      val node = new ErlangNode(Symbol("scala@localhost"), cookie)
      erl = Escript("receive_connection.escript")
      ReadLine(erl)
      node.ping(Symbol("test@localhost"), 1000) must ==(true)
    }
    
    "remove processes on exit" in {
      val node = new ErlangNode(Symbol("scala@localhost"), cookie)
      val pid = node.spawn[FailProcess]
      node.processes.get(pid) must beLike { case f : ProcessFiber => true }
      node.handleSend(pid, 'bah)
      Thread.sleep(100)
      Option(node.processes.get(pid)) must beNone
    }
  }
}