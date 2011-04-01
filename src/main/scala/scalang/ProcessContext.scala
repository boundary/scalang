package scalang

import scalang.node._

trait ProcessContext {
  def pid : Pid
  def node : ErlangNode
  def referenceCounter : ReferenceCounter
}