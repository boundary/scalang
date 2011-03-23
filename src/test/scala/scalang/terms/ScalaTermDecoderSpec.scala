package scalang.terms

import org.specs._
import scalang._
import org.jboss.netty._
import handler.codec.embedder._
import java.nio._
import buffer.ChannelBuffers._

class ScalaTermDecoderSpec extends Specification {
  "ScalaTermDecoder" should {
    val embedder = new DecoderEmbedder[Any](new ScalaTermDecoder)
    "read small integers" in {
      embedder.offer(copiedBuffer(ByteArray(97,2)))
      embedder.poll must ==(2)
    }
    
    "read 32 bit ints" in {
      embedder.offer(copiedBuffer(ByteArray(98,0,0,78,32)))
      embedder.poll must ==(20000)
    }
    
    "read string floats" in {
      embedder.offer(copiedBuffer(ByteArray(99,49,46,49,52,49,53,57,48,48,48,48,48,48,48,48,48,48,49,48,52,54,54,101,43,48,48,0,0,0,0,0)))
      embedder.poll must ==(1.14159)
    }
    
    "read atoms" in {
      embedder.offer(copiedBuffer(ByteArray(100,0,4,98,108,97,104)))
      embedder.poll must ==('blah)
    }
    
    "read pids" in {
      embedder.offer(copiedBuffer(ByteArray(103,100,0,13,110,111,110,111,100,101,64,110,111,104,
        111,115,116,0,0,0,31,0,0,0,0,0)))
      embedder.poll must ==(Pid(Symbol("nonode@nohost"), 31,0,0))
    }
    
    "read small tuples" in {
      embedder.offer(copiedBuffer(ByteArray(104,2,97,1,97,2)))
      embedder.poll must ==((1,2))
    }
    
    
    "read large tuples" in {
      embedder.offer(copiedBuffer(ByteArray(104,31,97,0,97,1,97,2,97,3,97,4,97,5,97,6,97,7,97,8,
        97,9,97,10,97,11,97,12,97,13,97,14,97,15,97,16,97,17,97,18,97,19,97,20,97,21,97,22,97,23,
        97,24,97,25,97,26,97,27,97,28,97,29,97,30)))
      embedder.poll must ==(new BigTuple((0 to 30).toSeq))
    }
    
    "read nils" in {
      embedder.offer(copiedBuffer(ByteArray(106)))
      embedder.poll must ==(Nil)
    }
    
    "read lists" in {
      embedder.offer(copiedBuffer(ByteArray(108,0,0,0,3,100,0,1,97,100,0,1,98,100,0,1,99,106)))
      embedder.poll must ==(List('a,'b,'c))
    }
    
    "read improper lists" in {
      embedder.offer(copiedBuffer(ByteArray(108,0,0,0,3,100,0,1,97,100,0,1,98,100,0,1,99,100,0,
        1,100)))
      embedder.poll must ==(ImproperList(List('a,'b,'c), 'd))
    }
    
    "read binaries" in {
      embedder.offer(copiedBuffer(ByteArray(109,0,0,0,4,98,108,97,104)))
      embedder.poll must ==(ByteBuffer.wrap(ByteArray(98,108,97,104)))
    }
    
    "read longs" in {
      embedder.offer(copiedBuffer(ByteArray(110,8,0,0,0,0,0,0,0,0,10)))
      embedder.poll must ==(720575940379279360L)
    }
    
    "read references" in {
      embedder.offer(copiedBuffer(ByteArray(114,0,3,100,0,13,110,111,110,111,100,101,64,110,111,104,111,115,116,0,0,0,0,99,0,0,0,0,0,0,0,0)))
      embedder.poll must ==(Reference(Symbol("nonode@nohost"), Seq(99,0,0), 0))
    }
    
    "small atoms" in {
      embedder.offer(copiedBuffer(ByteArray(115,1,97)))
      embedder.poll must ==('a)
    }
    
    "bit binaries" in {
      embedder.offer(copiedBuffer(ByteArray(77,0,0,0,1,7,120)))
      embedder.poll must ==(BitString(ByteBuffer.wrap(ByteArray(120, 7))))
    }
  }
}