package scalang

import util._

case class NodeConfig(
  poolFactory : ThreadPoolFactory,
  clusterListener : Option[ClusterListener])