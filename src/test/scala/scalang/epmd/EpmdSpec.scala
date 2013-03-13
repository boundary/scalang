package scalang.epmd

import org.specs._
import mock.Mockito
import java.lang.{Process => SysProcess}
import scalang._
import org.jboss.netty.channel.ChannelFuture

class EpmdSpec extends SpecificationWithJUnit with Mockito {
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

  "Epmd object" should {
    "return an Epmd directly" in {
      val noConnectConfig = new EpmdConfig("localhost", Epmd.defaultPort, connectOnInit = false)
      val epmd = Epmd(noConnectConfig)
      epmd.connected must(be(false))
    }

    "connect with retries" in {
      val epmd = mock[Epmd]
      val future = mock[ChannelFuture]
      epmd.connect.returns(future)

      // Always returns true so that it's never polled
      future.isDone.returns(true)

      // 'connected' is checked before 'future.isSuccess' since there should be a polling step
      // that's being skipped in each test.
      epmd.connected
        .returns(false)
        .thenReturns(false)
        .thenReturns(false)
        .thenReturns(false)
        .thenReturns(true)
      future.isSuccess
        .returns(false)
        .thenReturns(false)
        .thenReturns(false)
        .thenReturns(true)

      val retryCfg = new EpmdConfig(
        "localhost",
        Epmd.defaultPort,
        connectOnInit = true,
        connectionTimeout = Some(1),
        retries = Some(10),
        retryInterval = Some(1)
      )
      Epmd.connectWithRetries(epmd, retryCfg)

      there was 4.times(epmd).connect
    }
  }
}
