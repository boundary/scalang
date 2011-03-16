package scalang

import OtpConversions._
import com.ericsson.otp.erlang._

object ByteArray {
  def apply(xs : Int*) : Array[Byte] = {
    xs.map({x => x.toByte}).toArray
  }
}

object EAtom {
  def unapply(v : OtpErlangAtom) : Option[String] = {
    Some(v.atomValue)
  }
  def apply(v : String) = new OtpErlangAtom(v)
  def apply(v : Symbol) = new OtpErlangAtom(v.name)
}

object Head {
  def apply[T <: OtpErlangObject](erl : OtpErlangList) : T = {
    erl.getHead().asInstanceOf[T]
  }
  
  def unapply[A](v : Array[A]) : Option[A] = {
    if (v.size > 0)
      Some(v(0))
    else
      None
  }
}

object EList {
  
  def apply(seq : OtpErlangObject*) = new OtpErlangList(seq.toArray)
}

object ETuple {
  def unapplySeq(v : OtpErlangTuple) : Option[Seq[OtpErlangObject]] = {
    Some(v.elements.toSeq)
  }
  
  def apply(seq : OtpErlangObject*) = new OtpErlangTuple(seq.toArray)
}

object EBoolean {
  def unapply(v : OtpErlangAtom) : Option[Boolean] = {
    Some(v.booleanValue)
  }
  
  def apply(v : Boolean) = new OtpErlangBoolean(v)
}

object ELong {
  def unapply(v : OtpErlangLong) : Option[Long] = {
    Some(v.longValue)
  }
  
  def apply(v : Long) = new OtpErlangLong(v)
  def apply(v : java.lang.Integer) = new OtpErlangLong(v.longValue)
}

object EInt {
  def unapply(v : OtpErlangLong) : Option[Int] = {
    Some(v.intValue)
  }
  
  def apply(v : Int) = new OtpErlangLong(v)
}

object EShort {
  def unapply(v : OtpErlangLong) : Option[Short] = {
    Some(v.shortValue)
  }
  
  def apply(v : Short) = new OtpErlangLong(v)
}

object EFloat {
  def unapply(v : OtpErlangDouble) : Option[Double] = {
    Some(v.doubleValue)
  }
  
  def apply(v : Float) = new OtpErlangFloat(v)
}

object EDouble {
  def apply(v : Double) : OtpErlangDouble = {
    new OtpErlangDouble(v)
  }
}

object EBinary {
  def unapply(v : OtpErlangBinary) : Option[Array[Byte]] = {
    Some(v.binaryValue)
  }
  
  def apply(seq : Byte*) = new OtpErlangBinary(seq.toArray)
  def apply(str : String) = new OtpErlangBinary(str.getBytes)
}

object ESymbol {
  def unapply(v : OtpErlangObject) : Option[Symbol] = v match {
    case x : OtpErlangAtom => Some(Symbol(x.atomValue))
    case x : OtpErlangString => Some(Symbol(x.stringValue))
    case _ => None
  }
}

object EString {
  def unapply(v : OtpErlangObject) : Option[String] = v match {
    case x : OtpErlangAtom => Some(x.atomValue)
    case x : OtpErlangBinary => Some(new String(x.binaryValue))
    case x : OtpErlangString => Some(x.stringValue)
    case _ => None
  }
  
  def apply(v : String) = new OtpErlangString(v)
}