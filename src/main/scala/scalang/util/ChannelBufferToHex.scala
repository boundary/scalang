package scalang.util

import org.jboss.netty
import netty.buffer._

object ChannelBufferToHex {
  implicit def channelBufferToHex(buffer : ChannelBuffer) : CBTHWrapper = {
    new CBTHWrapper(buffer)
  }
}

class CBTHWrapper(buffer : ChannelBuffer) {
  def toHex : String = {
    buffer.resetReaderIndex
    val sb = new StringBuilder
    while(buffer.readable()) {
      val byte = buffer.readByte
      val str = Integer.toHexString(byte)
      if (str.length == 1) {
        sb ++= "0" + str
      } else {
        sb ++= str
      }
    }
    buffer.resetReaderIndex
    sb.toString
  }
}