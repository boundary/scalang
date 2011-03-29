package scalang.node

import org.specs._
import scalang._
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
    
    val cookie = "derp"
    
    "get connections from a remote node" in {
      val node = new ErlangNode(Symbol("test@localhost"), cookie)
      erl = ErlangVM("tmp@localhost", cookie, Some("io:format(\"~p~n\", [net_kernel:connect_node('test@localhost')])."))
      val read = new BufferedReader(new InputStreamReader(erl.getInputStream))
      println(read.readLine)
      node.channels.keySet.toSet must contain(Symbol("tmp@localhost"))
    }
  }
}