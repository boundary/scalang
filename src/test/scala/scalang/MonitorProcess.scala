package scalang

class MonitorProcess(ctx : ProcessContext) extends Process(ctx) {

  override def onMessage(msg : Any) = msg match {
    case (pid : Pid, sendTo : Pid) =>
      expectedRef = monitor(pid)
      expectedPid = pid
      this.sendTo = sendTo
      sendTo ! 'ok
    case ('exit, msg : Any) =>
      'ok
    case ('timeout) =>
      'ok
  }

  override def trapMonitorExit(pid : Any, ref : Reference, reason : Any) {
    if (expectedPid == pid && expectedRef == ref) {
      sendTo ! 'monitor_exit
    }
    else
      sendTo ! 'mismatch
  }

  var sendTo : Pid = null
  var expectedPid : Pid = null
  var expectedRef : Reference = null
}
