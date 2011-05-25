package scalang

import node._
import org.jboss.netty.buffer.ChannelBuffer

trait TypeFactory {
  def createType(name : Symbol, arity : Int, reader : TermReader) : Option[Any]
}

class TermReader(buffer : ChannelBuffer, decoder : ScalaTermDecoder) {
  var m : Int = 0
  
  def mark : TermReader = {
    m = buffer.readerIndex
    this
  }
  
  def reset : TermReader = {
    buffer.readerIndex(m)
    this
  }
  
  def readTerm : Any = {
    decoder.readTerm(buffer)
  }
  
  def readAs[A] : A = {
    readTerm.asInstanceOf[A]
  }
}