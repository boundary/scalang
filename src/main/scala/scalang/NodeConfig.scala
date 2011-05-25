package scalang

import util._

case class NodeConfig(
  poolFactory : ThreadPoolFactory = new DefaultThreadPoolFactory,
  clusterListener : Option[ClusterListener] = None,
  typeFactory : TypeFactory = NoneTypeFactory)
  
object NoneTypeFactory extends TypeFactory {
  def createType(name : Symbol, arity : Int, reader : TermReader) = None
}