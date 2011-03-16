package scalang.epmd

case class AliveReq(portNo : Int, nodeName : String)

case class AliveResp(result : Int, creation : Int)

case class PortPleaseReq(nodeName : String)

case class PortPleaseError(result : Int)

case class PortPleaseResp(portNo : Int, nodeName : String)