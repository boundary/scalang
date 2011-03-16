package scalang

import com.ericsson.otp.erlang._

object OtpConversions {
  implicit def erlangLongToInteger(erl : OtpErlangLong) : Int = erl.intValue
  implicit def erlangLongToByte(erl : OtpErlangLong) : Byte = erl.intValue.toByte
  implicit def erlangLongToLong(erl : OtpErlangLong) : Long = erl.longValue
  implicit def erlangAtomToString(erl : OtpErlangAtom) : String = erl.atomValue
  implicit def erlangAtomToBoolean(erl : OtpErlangAtom) : Boolean = erl.booleanValue
  implicit def erlangStringToString(erl : OtpErlangString) : String = erl.stringValue
  implicit def erlangListToList(erl : OtpErlangList) : List[OtpErlangObject] = erl.elements.toList
  implicit def erlangBinaryToBytes(erl : OtpErlangBinary) : Seq[Byte] = erl.binaryValue
  implicit def bytestoErlang(ary : Array[Byte]) = new OtpErlangBinary(ary)
  implicit def intToErlangLong(v : java.lang.Integer) = new OtpErlangLong(v.longValue)
  implicit def stringToErlangString(v : String) : OtpErlangString = new OtpErlangString(v)
  implicit def longToErlangLong(v : Long) : OtpErlangLong = new OtpErlangLong(v)
  implicit def intToErlangLong(v : Int) : OtpErlangLong = new OtpErlangLong(v)
  implicit def symbolToAtom(v : Symbol) : OtpErlangAtom = new OtpErlangAtom(v.name)
  implicit def tuple2ToOtp[A <% OtpErlangObject,B <% OtpErlangObject](t : (A,B)) : OtpErlangTuple = {
    ETuple(t._1, t._2)
  }
  
  implicit def tuple3ToOtp[A <% OtpErlangObject,B <% OtpErlangObject,C <% OtpErlangObject](t : (A,B,C)) : OtpErlangTuple = {
    ETuple(t._1, t._2, t._3)
  }
  
  implicit def tuple4ToOtp[A <% OtpErlangObject,B <% OtpErlangObject,C <% OtpErlangObject,D <% OtpErlangObject](t : (A,B,C,D)) : OtpErlangTuple = {
    ETuple(t._1, t._2, t._3, t._4)
  }
  
  implicit def seqToErlangList[A](list : Seq[A])(implicit view : A => OtpErlangObject) : OtpErlangList = {
    EList(list.map(view(_)).toSeq : _*)
  }
}