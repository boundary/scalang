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
import java.math.BigInteger
import netty.buffer._
import scalang._
import java.util.Formatter
import java.util.{List => JList}
import scalang.util.ByteArray
import scalang.util.CamelToUnder._
import com.yammer.metrics.scala._
import com.boundary.logula.Logging

class ScalaTermEncoder(peer: Symbol, encoder: TypeEncoder = NoneTypeEncoder) extends OneToOneEncoder with Logging with Instrumented {

  val encodeTimer = metrics.timer("encoding", peer.name)

  override def encode(ctx : ChannelHandlerContext, channel : Channel, obj : Any) : Object = {
    log.debug("sending msg %s", obj)
    encodeTimer.time {
      val buffer = ChannelBuffers.dynamicBuffer(512)
      //write distribution header
      buffer.writeBytes(ByteArray(112,131))
      obj match {
        case Tock =>
          buffer.clear()
        case LinkMessage(from, to) =>
          encodeObject(buffer, (1, from, to))
        case SendMessage(to, msg) =>
          encodeObject(buffer, (2, Symbol(""), to))
          buffer.writeByte(131)
          encodeObject(buffer, msg)
        case ExitMessage(from, to, reason) =>
          encodeObject(buffer, (3, from, to, reason))
        case UnlinkMessage(from, to) =>
          encodeObject(buffer, (4, from, to))
        case RegSend(from, to, msg) =>
          encodeObject(buffer, (6, from, Symbol(""), to))
          buffer.writeByte(131)
          encodeObject(buffer, msg)
        case Exit2Message(from, to, reason) =>
          encodeObject(buffer, (8, from, to, reason))
        case MonitorMessage(monitoring, monitored, ref) =>
          encodeObject(buffer, (19, monitoring, monitored, ref))
        case DemonitorMessage(monitoring, monitored, ref) =>
          encodeObject(buffer, (20, monitoring, monitored, ref))
        case MonitorExitMessage(monitored, monitoring, ref, reason) =>
          encodeObject(buffer, (21, monitoring, monitored, ref, reason))
      }

      buffer
    }
  }

  def encodeObject(buffer : ChannelBuffer, obj : Any) : Unit = obj match {
    case encoder(_) =>
      encoder.encode(obj, buffer)
    case i : Int if i >= 0 && i <= 255 =>
      writeSmallInteger(buffer, i)
    case i : Int =>
      writeInteger(buffer, i)
    case l : Long =>
      writeLong(buffer, l)
    case f : Float =>
      writeFloat(buffer, f)
    case d : Double =>
      writeFloat(buffer, d)
    case true =>
      writeAtom(buffer, 'true)
    case false =>
      writeAtom(buffer, 'false)
    case s : Symbol =>
      writeAtom(buffer, s)
    case Reference(node, id, creation) => //we only emit new references
      buffer.writeByte(114)
      buffer.writeShort(id.length)
      writeAtom(buffer, node)
      buffer.writeByte(creation)
      for (i <- id) {
        buffer.writeInt(i)
      }
    case Port(node, id, creation) =>
      buffer.writeByte(102)
      writeAtom(buffer, node)
      buffer.writeInt(id)
      buffer.writeByte(creation)
    case Pid(node, id, serial, creation) =>
      buffer.writeByte(103)
      writeAtom(buffer, node)
      buffer.writeInt(id)
      buffer.writeInt(serial)
      buffer.writeByte(creation)
    case Fun(pid, module, index, uniq, vars) =>
      buffer.writeByte(117)
      buffer.writeInt(vars.length)
      encodeObject(buffer, pid)
      writeAtom(buffer, module)
      encodeObject(buffer, index)
      encodeObject(buffer, uniq)
      for (v <- vars) {
        encodeObject(buffer, v)
      }
    case s : String =>
      buffer.writeByte(107)
      val bytes = s.getBytes
      buffer.writeShort(bytes.length)
      buffer.writeBytes(bytes)
    case ImproperList(list, tail) =>
      writeList(buffer, list, tail)
    case Nil =>
      buffer.writeByte(106)
    case l : JList[Any] =>
      writeJList(buffer, l, Nil)
    case l : List[Any] =>
      writeList(buffer, l, Nil)
    case b : BigInteger =>
      writeBigInt(buffer, b)
    case a : Array[Byte] =>
      writeBinary(buffer, a)
    case b : ByteBuffer =>
      writeBinary(buffer, b)
    case BitString(b, i) =>
      writeBinary(buffer, b, i)
    case b : BigTuple =>
      writeBigTuple(buffer, b)
    case p : Product =>
      writeProduct(buffer, p)
  }

  def writeBinary(buffer : ChannelBuffer, b : ByteBuffer) {
    val length = b.remaining
    buffer.writeByte(109)
    buffer.writeInt(length)
    buffer.writeBytes(b)
  }

  def writeBinary(buffer : ChannelBuffer, a : Array[Byte]) {
    val length = a.length
    buffer.writeByte(109)
    buffer.writeInt(length)
    buffer.writeBytes(a)
  }

  def writeBinary(buffer : ChannelBuffer, b : ByteBuffer, i : Int) {
    val length = b.remaining
    buffer.writeByte(77)
    buffer.writeInt(length)
    buffer.writeByte(i)
    buffer.writeBytes(b)
  }

  def writeLong(buffer : ChannelBuffer, l : Long) {
    val sign = if (l < 0) 1 else 0
    val bytes = ByteBuffer.allocate(8)
    bytes.order(ByteOrder.LITTLE_ENDIAN)
    bytes.putLong(l)
    buffer.writeByte(110)
    buffer.writeByte(8)
    buffer.writeByte(sign)
    bytes.flip
    buffer.writeBytes(bytes)
  }

  def writeBigInt(buffer : ChannelBuffer, big : BigInteger) {
    val bytes = big.toByteArray
    val sign = if (big.signum < 0) 1 else 0
    val length = bytes.length
    if (length < 255) {
      buffer.writeByte(110)
      buffer.writeByte(length)
    } else {
      buffer.writeByte(111)
      buffer.writeInt(length)
    }
    buffer.writeByte(sign)
    for (i <- (1 to length)) {
      buffer.writeByte(bytes(length - i))
    }
  }

  def writeList(buffer : ChannelBuffer, list : List[Any], tail : Any) {
    buffer.writeByte(108)
    buffer.writeInt(list.size)
    for (element <- list) {
      encodeObject(buffer, element)
    }
    encodeObject(buffer, tail)
  }

  def writeJList(buffer : ChannelBuffer, list : JList[Any], tail : Any) {
    buffer.writeByte(108)
    buffer.writeInt(list.size)
    val i = list.iterator()
    while(i.hasNext) {
      encodeObject(buffer, i.next())
    }
    encodeObject(buffer, tail)
  }

  def writeAtom(buffer : ChannelBuffer, s : Symbol) {
    val bytes = s.name.getBytes
    if (bytes.length < 256) {
      buffer.writeByte(115)
      buffer.writeByte(bytes.length)
    } else {
      buffer.writeByte(100)
      buffer.writeShort(bytes.length)
    }
    buffer.writeBytes(bytes)
  }

  def writeSmallInteger(buffer : ChannelBuffer, i : Int) {
    buffer.writeByte(97)
    buffer.writeByte(i)
  }

  def writeInteger(buffer : ChannelBuffer, i : Int) {
    buffer.writeByte(98)
    buffer.writeInt(i)
  }

  def writeFloat(buffer : ChannelBuffer, d : Double) {
    if (d.isNaN) {
      writeAtom(buffer, 'nan)
    } else if (d.isPosInfinity) {
      writeAtom(buffer, 'infinity)
    } else if (d.isNegInfinity) {
      writeAtom(buffer, Symbol("-infinity"))
    } else {
      buffer.writeByte(70)
      buffer.writeLong(java.lang.Double.doubleToLongBits(d))
    }
  }

  def writeStringFloat(buffer : ChannelBuffer, d : Double) {
    val formatter = new Formatter
    formatter.format("%.20e", d.asInstanceOf[AnyRef])
    val str = formatter.toString.getBytes
    buffer.writeByte(99)
    buffer.writeBytes(str)
  }

  def writeProduct(buffer : ChannelBuffer, p : Product) {
    val name = p.productPrefix
    if (name.startsWith("Tuple")) {
      writeTuple(buffer, None, p)
    } else {
      writeTuple(buffer, Some(name.camelToUnderscore), p)
    }
  }

  def writeTuple(buffer : ChannelBuffer, tag : Option[String], p : Product) {
    val length = tag.size + p.productArity
    buffer.writeByte(104)
    buffer.writeByte(length)
    for (t <- tag) {
      writeAtom(buffer, Symbol(t))
    }
    for (element <- p.productIterator) {
      encodeObject(buffer, element)
    }
  }

  def writeBigTuple(buffer : ChannelBuffer, tuple : BigTuple) {
    val length = tuple.productArity
    if (length < 255) {
      buffer.writeByte(104)
      buffer.writeByte(length)
    } else {
      buffer.writeByte(105)
      buffer.writeInt(length)
    }
    for (element <- tuple.productIterator) {
      encodeObject(buffer, element)
    }
  }
}
