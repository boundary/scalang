package scalang.node

import scalang._

case class LinkMessage(from : Pid, to : Pid)

case class SendMessage(to : Pid, msg : Any)

case class ExitMessage(from : Pid, to : Pid, reason : Any)

case class Exit2Message(from : Pid, to : Pid, reason : Any)

case class UnlinkMessage(from : Pid, to : Pid)

case class NodeLink()

case class RegSend(from : Pid, to : Symbol, msg : Any)

//must implement trace tags later

//monitors too

class DistributedProtocolException(msg : String) extends Exception(msg)