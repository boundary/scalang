package scalang.util

/**
 * State Machine
 * example usage:
 *
 */

trait StateMachine {

  def start : Symbol
  val mutex = new Object
  protected var stateList : List[State] = null
  @volatile protected var currentState = start

  def event(evnt : Any) {
    mutex.synchronized {
      if (currentState == null) {
        currentState = start
      }
      val state = stateList.find(_.name == currentState).getOrElse(throw new UndefinedStateException("state " + currentState + " is undefined"))
      val nextState = state.event(evnt)
      stateList.find(_.name == nextState).getOrElse(throw new UndefinedStateException("state " + currentState + " is undefined"))
      currentState = nextState
    }
  }

  protected def states(states : State*) {
    stateList = states.toList
  }

  protected def state(name : Symbol, transitions : PartialFunction[Any,Symbol]) = State(name, transitions)

  case class State(name : Symbol, transitions : PartialFunction[Any,Symbol]) {
    def event(evnt : Any) : Symbol = {
      if (!transitions.isDefinedAt(evnt)) {
        throw new UnexpectedEventException("State " + name + " does not have a transition for event " + evnt)
      } else {
        transitions(evnt)
      }
    }
  }

  class UnexpectedEventException(msg : String) extends Exception(msg)

  class UndefinedStateException(msg : String) extends Exception(msg)
}
