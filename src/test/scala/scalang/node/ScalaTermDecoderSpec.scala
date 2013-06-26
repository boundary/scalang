package scalang.node

import org.specs.SpecificationWithJUnit
import org.jboss.netty.buffer.ChannelBuffers
import java.nio.charset.Charset

class ScalaTermDecoderSpec extends SpecificationWithJUnit {
  "ScalaTermDecoder" should {
    "decode a string" in {
      val buffer = ChannelBuffers.copiedBuffer("abc", Charset.forName("utf-8"))
      val decoded = ScalaTermDecoder.fastString(buffer, 3)
      decoded must ==("abc")
      decoded.length must ==(3)
    }
  }
}
