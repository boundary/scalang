package scalang.terms

import org.jboss.netty
import netty.handler.codec.oneone._
import netty.channel._
import java.nio._
import netty.buffer._
import scala.annotation.tailrec
import scalang._

class ScalaTermDecoder extends OneToOneDecoder {
  
  def decode(ctx : ChannelHandlerContext, channel : Channel, obj : Any) : Object = {
    val buffer = obj.asInstanceOf[ChannelBuffer]
    readTerm(buffer).asInstanceOf[AnyRef]
  }
  
  def readTerm(buffer : ChannelBuffer) : Any = {
    buffer.readByte match {
      case 97 => //small integer
        buffer.readUnsignedByte
      case 98 => //integer
        buffer.readInt
      case 99 => //float string
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        val floatString = new String(bytes)
        floatString.toDouble
      case 100 => //atom
        val len = buffer.readShort
        val bytes = new Array[Byte](len)
        buffer.readBytes(bytes)
        Symbol(new String(bytes))
      case 101 => //reference
        val node = readTerm(buffer).asInstanceOf[Symbol]
        val id = buffer.readInt
        val creation = buffer.readUnsignedByte
        Reference(node, Seq(id), creation)
      case 102 => //port
        val node = readTerm(buffer).asInstanceOf[Symbol]
        val id = buffer.readInt
        val creation = buffer.readByte
        Port(node, id, creation)
      case 103 => //pid
        val node = readTerm(buffer).asInstanceOf[Symbol]
        val id = buffer.readInt
        val serial = buffer.readInt
        val creation = buffer.readUnsignedByte
        Pid(node,id,serial,creation)
      case 104 => //small tuple -- will be a scala tuple up to size 22
        val arity = buffer.readUnsignedByte
        readTuple(arity, buffer)
      case 105 => //large tuple -- will be an untyped erlang tuple
        val arity = buffer.readInt
        readTuple(arity, buffer)
      case 106 => //nil
        Nil
      case 107 => //string
        val length = buffer.readShort
        val bytes = new Array[Byte](length)
        buffer.readBytes(bytes)
        new String(bytes)
      case 108 => //list
        val length = buffer.readInt
        val (list, improper) = readList(length, buffer)
        improper match {
          case None => list
          case Some(imp) => new ImproperList(list, imp)
        }
      case 109 => //binary
        val length = buffer.readInt
        val byteBuffer = ByteBuffer.allocate(length)
        buffer.readBytes(byteBuffer)
        byteBuffer.flip
        byteBuffer
      case 110 => //small bignum
        val length = buffer.readUnsignedByte
        val sign = buffer.readUnsignedByte match {
          case 0 => 1
          case _ => -1
        }
        if (length <= 8) {
          readLittleEndianLong(length, sign, buffer)
        } else {
          val bytes = readReversed(length, buffer)
          BigInt(sign, bytes)
        }
      case 111 => //large bignum
        val length = buffer.readInt
        val sign = buffer.readUnsignedByte match {
          case 0 => 1
          case _ => -1
        }
        if (length <= 8) {
          readLittleEndianLong(length, sign, buffer)
        } else {
          val bytes = readReversed(length, buffer)
          BigInt(sign, bytes)
        }
      case 114 => //new reference
        val length = buffer.readShort
        val node = readTerm(buffer).asInstanceOf[Symbol]
        val creation = buffer.readUnsignedByte
        val id = (for(n <- (0 until length)) yield {
          buffer.readInt
        }).toSeq
        Reference(node, id, creation)
      case 115 => //small atom
        val length = buffer.readUnsignedByte
        val bytes = new Array[Byte](length)
        buffer.readBytes(bytes)
        Symbol(new String(bytes))
      case 117 => //fun
        val numFree = buffer.readInt
        val pid = readTerm(buffer).asInstanceOf[Pid]
        val module = readTerm(buffer).asInstanceOf[Symbol]
        val index = readTerm(buffer).asInstanceOf[Int]
        val uniq = readTerm(buffer).asInstanceOf[Int]
        val vars = (for(n <- (0 until numFree)) yield {
          readTerm(buffer)
        }).toSeq
        Fun(pid,module,index,uniq,vars)
      case 112 => //new fun
        val size = buffer.readInt
        val arity = buffer.readUnsignedByte
        val uniq = new Array[Byte](16)
        buffer.readBytes(uniq)
        val index = buffer.readInt
        val numFree = buffer.readInt
        val module = readTerm(buffer).asInstanceOf[Symbol]
        val oldIndex = readTerm(buffer).asInstanceOf[Int]
        val oldUniq = readTerm(buffer).asInstanceOf[Int]
        val pid = readTerm(buffer).asInstanceOf[Pid]
        val vars = (for(n <- (0 until numFree)) yield {
          readTerm(buffer)
        }).toSeq
        NewFun(pid, module, oldIndex, oldUniq, arity, index, uniq, vars)
      case 113 => //export
        val module = readTerm(buffer).asInstanceOf[Symbol]
        val function = readTerm(buffer).asInstanceOf[Symbol]
        val arity = readTerm(buffer).asInstanceOf[Int]
        ExportFun(module, function, arity)
      case 77 => //bit binary
        val length = buffer.readInt
        val bits = buffer.readUnsignedByte
        val byteBuffer = ByteBuffer.allocate(length)
        buffer.readBytes(byteBuffer)
        byteBuffer.flip
        BitString(byteBuffer, bits)
      case 70 => //new float
        buffer.readDouble
    }
  }
  
  def readLittleEndianLong(length : Int, sign : Int, buffer : ChannelBuffer) : Long = {
    val bytes = new Array[Byte](8)
    buffer.readBytes(bytes, 0, length)
    val little = ChannelBuffers.wrappedBuffer(ByteOrder.LITTLE_ENDIAN, bytes)
    little.readLong
  }
  
  def readReversed(length : Int, buffer : ChannelBuffer) : Array[Byte] = {
    val bytes = new Array[Byte](length)
    for (n <- (1 to length)) {
      bytes(length-n) = buffer.readByte
    }
    bytes
  }
  
  def readList(length : Int, buffer : ChannelBuffer) : (List[Any], Option[Any]) = {
    val hd = readTerm(buffer)
    length match {
      case 1 => readTerm(buffer) match {
        case Nil => (hd :: Nil, None)
        case improper => (hd :: Nil, Some(improper))
      }
      case _ => 
        val (tail, improper) = readList(length-1, buffer)
        (hd :: tail, improper)
    }
  }
  
  def readTuple(arity : Int, buffer : ChannelBuffer) = arity match {
    case 1 => (readTerm(buffer))
    case 2 => (readTerm(buffer), readTerm(buffer))
    case 3 => (readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 4 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 5 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 6 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 7 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 8 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 9 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 10 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 11 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 12 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 13 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 14 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 15 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 16 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 17 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 18 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 19 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 20 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 21 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 22 => (readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case _ => readBigTuple(arity, buffer)
  }
  
  def readBigTuple(arity : Int, buffer : ChannelBuffer) : BigTuple = {
    val elements = (for(n <- (0 until arity)) yield {
      readTerm(buffer)
    }).toSeq
    new BigTuple(elements)
  }
}