package scalang

import scalang.node._
import org.jetlang.fibers._

trait ProcessContext {
  def pid : Pid
  def node : ErlangNode
  def referenceCounter : ReferenceCounter
  def fiber : Fiber
}