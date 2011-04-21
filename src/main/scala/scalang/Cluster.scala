package scalang

class Cluster(ctx : ProcessContext) extends Process(ctx) {
  @volatile var nodes = Set[Symbol]()
  
  def onMessage(msg : Any) = msg match {
    case ('cluster, pid : Pid, ref : Reference) =>
      pid ! ('cluster, ref, nodes.toList)
    case ('nodeup, node : Symbol) =>
      nodes += node
    case ('nodedown, node : Symbol) =>
      nodes -= node
  }
  
}