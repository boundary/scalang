package scalang

import scala.collection.immutable.LinearSeq
import scala.collection.mutable.StringBuilder

case class ImproperList(under : List[Any], lastTail : Any) extends LinearSeq[Any] {
  override def isEmpty = under.isEmpty
  override def head = under.head
  override def tail = under.tail
  
  override def apply(idx : Int) = under(idx)
  
  override def length = under.length
  
  override def toString : String = {
    val buffer = new StringBuilder
    buffer ++= "ImproperList("
    buffer ++= under.toString
    buffer ++= ", "
    buffer ++= lastTail.toString
    buffer ++= ")"
    buffer.toString
  }
}