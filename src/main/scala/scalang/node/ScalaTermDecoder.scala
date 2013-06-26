//
// Copyright 2011, Boundary
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package scalang.node

import org.jboss.netty
import netty.handler.codec.oneone._
import netty.channel._
import java.nio._
import netty.buffer._
import scala.annotation.tailrec
import scalang._
import com.yammer.metrics.scala._
import scala.collection.mutable.ArrayBuffer
import overlock.cache.CachedSymbol
import sun.misc.Unsafe

object ScalaTermDecoder {
  private val field = classOf[Unsafe].getDeclaredField("theUnsafe")
  field.setAccessible(true)
  val unsafe = field.get(classOf[ScalaTermDecoder]).asInstanceOf[Unsafe]

  val stringValueOffset = unsafe.objectFieldOffset(classOf[String].getDeclaredField("value"))
  private[this] val stringUsesCount = classOf[String].getDeclaredFields.map{f => f.getName}.contains("count")
  val stringCountOffset = {
    if (stringUsesCount)
      unsafe.objectFieldOffset(classOf[String].getDeclaredField("count"))
    else
      0
  }

  // Initializes a string by creating an empty String object, then populating it with our own
  // backing char[] to prevent allocating / copying between byte[] or char[] twice.
  def fastString(buffer: ChannelBuffer, length: Int) : String = {
    val destArray = new Array[Char](length)

    var i = 0
    while (i < length) {
      destArray(i) = buffer.readByte().asInstanceOf[Char]
      i+=1
    }

    val result = unsafe.allocateInstance(classOf[String]).asInstanceOf[String]
    unsafe.putObject(result, stringValueOffset, destArray)
    if (stringUsesCount) {
      unsafe.putInt(result, stringCountOffset, length)
    }
    result
  }
}

class ScalaTermDecoder(peer : Symbol, factory : TypeFactory, decoder : TypeDecoder = NoneTypeDecoder) extends OneToOneDecoder with Instrumented {
  val decodeTimer = metrics.timer("decoding", peer.name)

  def decode(ctx : ChannelHandlerContext, channel : Channel, obj : Any) : Object = obj match {
    case buffer : ChannelBuffer =>
      if (buffer.readableBytes > 0) {
        decodeTimer.time {
          readMessage(buffer)
        }
      } else {
        Tick
      }
    case _ =>
      obj.asInstanceOf[AnyRef]
  }

  def readMessage(buffer : ChannelBuffer) : AnyRef = {
    val t = buffer.readByte
    if (t != 112) throw new DistributedProtocolException("Got message of type " + t)

    val version = buffer.readUnsignedByte
    if (version != 131) throw new DistributedProtocolException("Version mismatch " + version)
    readTerm(buffer) match {
      case (1, from : Pid, to : Pid) =>
        LinkMessage(from, to)
      case (2, _, to : Pid) =>
        buffer.skipBytes(1)
        val msg = readTerm(buffer)
        SendMessage(to, msg)
      case (3, from : Pid, to : Pid, reason : Any) =>
        ExitMessage(from, to, reason)
      case (4, from : Pid, to : Pid) =>
        UnlinkMessage(from, to)
      case (6, from : Pid, _, to : Symbol) =>
        buffer.skipBytes(1)
        val msg = readTerm(buffer)
        RegSend(from, to, msg)
      case (8, from : Pid, to : Pid, reason : Any) =>
        Exit2Message(from, to, reason)
      case (19, monitoring : Pid, monitored: Any, ref : Reference) =>
        MonitorMessage(monitoring, monitored, ref)
      case (20, monitoring : Pid, monitored : Any, ref : Reference) =>
        DemonitorMessage(monitoring, monitored, ref)
      case (21, monitored : Any, monitoring : Pid, ref : Reference, reason : Any) =>
        MonitorExitMessage(monitored, monitoring, ref, reason)
    }
  }

  def readTerm(buffer : ChannelBuffer) : Any = {
    val typeOrdinal : Int = buffer.readUnsignedByte
    typeOrdinal match {
      case decoder(_) =>
        decoder.decode(typeOrdinal, buffer)
      case 131 => //version derp
        readTerm(buffer)
      case 97 => //small integer
        buffer.readUnsignedByte.toInt
      case 98 => //integer
        buffer.readInt
      case 99 => //float string
        val floatString = ScalaTermDecoder.fastString(buffer, 31)
        floatString.toDouble
      case 100 => //atom OR boolean
        val len = buffer.readShort
        val str = ScalaTermDecoder.fastString(buffer, len)
        CachedSymbol(str) match {
          case 'true => true
          case 'false => false
          case atom => atom
        }
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
        ScalaTermDecoder.fastString(buffer, length)
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
        val str = ScalaTermDecoder.fastString(buffer, length)
        CachedSymbol(str) match {
          case 'true => true
          case 'false => false
          case atom => atom
        }
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
    var i = 0
    val b = new ArrayBuffer[Any](length)
    while (i <= length) {
      val term = readTerm(buffer)
      if (i == length) {
        term match {
          case Nil => return (b.toList, None)
          case improper => return (b.toList, Some(improper))
        }
      } else {
        b += term
        i += 1
      }
    }
    (b.toList, None)
  }

  def readTuple(arity : Int, buffer : ChannelBuffer) = {
    readTerm(buffer) match {
      case name : Symbol =>
        val reader = new TermReader(buffer, this)
        factory.createType(name, arity, reader) match {
          case Some(obj) => obj
          case None =>
            readVanillaTuple(name, arity, buffer)
        }
      case first =>
        readVanillaTuple(first, arity, buffer)
    }
  }

  def readVanillaTuple(first : Any, arity : Int, buffer : ChannelBuffer) : Any = arity match {
    case 1 => (first)
    case 2 => (first, readTerm(buffer))
    case 3 => (first, readTerm(buffer), readTerm(buffer))
    case 4 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 5 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 6 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 7 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 8 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 9 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 10 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 11 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 12 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 13 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 14 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 15 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 16 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 17 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 18 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 19 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 20 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 21 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case 22 => (first, readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer), readTerm(buffer))
    case _ => readBigTuple(first, arity, buffer)
  }

  def readBigTuple(first : Any, arity : Int, buffer : ChannelBuffer) : BigTuple = {
    val elements = (for(n <- (1 until arity)) yield {
      readTerm(buffer)
    }).toSeq
    new BigTuple(Seq(first) ++ elements)
  }
}
