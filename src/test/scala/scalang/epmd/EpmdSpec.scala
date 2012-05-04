package scalang.epmd

import org.specs._
import org.specs.runner._
import java.lang.{Process => SysProcess}
import scalang._

class EpmdSpec extends SpecificationWithJUnit {
  "Epmd" should {
    var proc : SysProcess = null
    doBefore {
      proc = EpmdCmd()
    }

    doAfter {
      proc.destroy
      proc.waitFor
    }

    "publish a port to a running epmd instance" in {
      val epmd = Epmd("localhost")
      val creation = epmd.alive(5480, "fuck@you.com")
      creation must beLike { case Some(v : Int) => true }
      epmd.close
    }

    "retrieve a port" in {
      val epmdPublish = Epmd("localhost")
      epmdPublish.alive(5480, "fuck@you.com")

      val epmdQuery = Epmd("localhost")
      val portPlease = epmdQuery.lookupPort("fuck@you.com")
      portPlease must beSome(5480)

      epmdPublish.close
      epmdQuery.close
    }
  }
}
