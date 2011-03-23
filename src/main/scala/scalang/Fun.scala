package scalang

case class Fun(pid : Pid, module : Symbol, index : Int, uniq : Int, vars : Seq[Any])

case class NewFun(pid : Pid, module : Symbol, oldIndex : Int, oldUniq : Int, arity : Int, index : Int, uniq : Seq[Byte], vars : Seq[Any])
  
case class ExportFun(module : Symbol, function : Symbol, arity : Int)
