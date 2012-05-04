package scalang.util

object ByteArray {
  def apply(values : Int*) : Array[Byte] = {
    Array[Byte](values.map(_.toByte) : _*)
  }
}
