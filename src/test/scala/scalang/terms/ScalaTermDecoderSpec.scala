package scalang.terms

import org.specs._
import scalang.node.{Foo, Derp, CaseClassFactory, ScalaTermDecoder}
import org.jboss.netty._
import handler.codec.embedder._
import java.nio._
import buffer.ChannelBuffers._
import scalang.util._
import scalang._

class ScalaTermDecoderSpec extends SpecificationWithJUnit {
  "ScalaTermDecoder" should {
    "decode regular terms" in {
      val decoder = new ScalaTermDecoder('test, NoneTypeFactory)
      
      "read small integers" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(97,2)))
        thing must ==(2)
      }

      "read 32 bit ints" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(98,0,0,78,32)))
        thing must ==(20000)
      }

      "read string floats" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(99,49,46,49,52,49,53,57,48,48,48,48,48,48,48,48,48,48,49,48,52,54,54,101,43,48,48,0,0,0,0,0)))
        thing must ==(1.14159)
      }

      "read atoms" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(100,0,4,98,108,97,104)))
        thing must ==('blah)
      }

      "read strings" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(107,0,4,98,108,97,104)))
        thing must ==("blah")
      }

      "read pids" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(103,100,0,13,110,111,110,111,100,101,64,110,111,104,
          111,115,116,0,0,0,31,0,0,0,0,0)))
        thing must ==(Pid(Symbol("nonode@nohost"), 31,0,0))
      }

      "read small tuples" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(104,2,97,1,97,2)))
        thing must ==((1,2))
      }


      "read large tuples" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(104,31,97,0,97,1,97,2,97,3,97,4,97,5,97,6,97,7,97,8,
          97,9,97,10,97,11,97,12,97,13,97,14,97,15,97,16,97,17,97,18,97,19,97,20,97,21,97,22,97,23,
          97,24,97,25,97,26,97,27,97,28,97,29,97,30)))
        thing must ==(new BigTuple((0 to 30).toSeq))
      }

      "read nils" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(106)))
        thing must ==(Nil)
      }

      "read lists" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(108,0,0,0,3,100,0,1,97,100,0,1,98,100,0,1,99,106)))
        thing must ==(List('a,'b,'c))
      }

      "read improper lists" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(108,0,0,0,3,100,0,1,97,100,0,1,98,100,0,1,99,100,0,
          1,100)))
        thing must ==(ImproperList(List('a,'b,'c), 'd))
      }

      "read binaries" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(109,0,0,0,4,98,108,97,104)))
        thing must ==(ByteBuffer.wrap(ByteArray(98,108,97,104)))
      }

      "read longs" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(110,8,0,0,0,0,0,0,0,0,10)))
        thing must ==(720575940379279360L)
      }

      "read references" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(114,0,3,100,0,13,110,111,110,111,100,101,64,110,111,104,111,115,116,0,0,0,0,99,0,0,0,0,0,0,0,0)))
        thing must ==(Reference(Symbol("nonode@nohost"), Seq(99,0,0), 0))
      }

      "small atoms" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(115,1,97)))
        thing must ==('a)
      }

      "bit binaries" in {
        val thing = decoder.readTerm(copiedBuffer(ByteArray(77,0,0,0,1,7,120)))
        thing must ==(BitString(ByteBuffer.wrap(ByteArray(120)), 7))
      }
      
      "read case objects" in {
        val dec = new ScalaTermDecoder('test, new CaseClassFactory(Seq("scalang.node"), Map[String,Class[_]]()))
        //{foo, "balls", 1245, 60.0}
        val foo = dec.readTerm(copiedBuffer(ByteArray(104,4,100,0,3,102,111,111,107,0,5,98,97,108,108,115,98,0,0,4,221,99,54,
                                                      46,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,101,43,48,49,
                                                      0,0,0,0,0)))
        foo must haveClass[Foo]
        val realFoo = foo.asInstanceOf[Foo]
        realFoo.balls must ==("balls")
        realFoo.integer must ==(1245)
        realFoo.float must ==(60.0)
      }
      
      "read typeMapped objects" in {
        val dec = new ScalaTermDecoder('test, new CaseClassFactory(Nil, Map("herp" -> classOf[Derp])))
        //{herp, 6234234234234234234, 1260.0, "gack"}
        val derp = dec.readTerm(copiedBuffer(ByteArray(104,4,100,0,4,104,101,114,112,110,8,0,122,101,28,114,1,115,132,86,99,49,
                                                       46,50,54,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,48,101,43,48,51,
                                                       0,0,0,0,0,107,0,4,103,97,99,107)))
        derp must haveClass[Derp]
        val realDerp = derp.asInstanceOf[Derp]
        realDerp.long must ==(6234234234234234234L)
        realDerp.double must ==(1260.0)
        realDerp.gack must ==("gack")
      }
    }
    
    "decode full distribution packets" in {
      new DecoderEmbedder[Any](new ScalaTermDecoder('test, new CaseClassFactory(Nil, Map[String,Class[_]]())))
    }
  }
}
