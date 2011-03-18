/**
 * Razvan's public code. Copyright 2008 based on Apache license (share alike) see LICENSE.txt for
 * details.
 */
package razie

import scala.util.matching.Regex

/** state machines have states, transitions and consume events */
object SM {
   val logger = razie.Log
   
   type Cback = (StateMachine, Transition, Event) => Unit
   
   /** marker, see equals() - instances can compare to many instances */
   trait Many { 
      def ~= (other:Any) : Boolean
      override def equals (other:Any) : Boolean = this ~= other
   } 

   // -------------------------------- states
   
   trait State { 
      def name : String // every state has a name
      def ~= (other:Any) : Boolean = 
         if (other.isInstanceOf[Many]) other.asInstanceOf[Many] ~= (this) else this == other 
   }
   
   case class SState (name:String) extends State { require(name != null) }
   object NullState extends SState ("Null")
   case class REState (regex:Regex) extends SState (regex.toString) with Many {
      override def equals (other:Any) : Boolean = this ~= other
      override def ~= (other:Any) : Boolean = other match {
         case SState(n) => n matches regex.pattern.pattern // TODO stupid matching :)))
         case _ => false
      }
   }
   
   // -------------------------------- events
   
   trait Event { def ~= (other:Any) : Boolean = if (other.isInstanceOf[Many]) other.asInstanceOf[Many] ~= (this) else this == other }
   case class IEvent    (i:Int, var name:String=null) extends Event { if(name==null) name = ""+i.toChar }
   case class SEvent    (name:String) extends Event
   case class FEvent   (f:Event=>Boolean, var name:String=null) extends Event with Many { 
      if(name==null) name = "F event"
         
      override def equals (other:Any) : Boolean = this ~= other
      override def ~= (other:Any) : Boolean     =  f (other.asInstanceOf[Event])
   }
   case class REEvent   (regex:Regex, var name:String=null) extends Event with Many { 
      if(name==null) name = regex.toString
         
      override def equals (other:Any) : Boolean = this ~= other
      override def ~= (other:Any) : Boolean = 
         if (other.isInstanceOf[Many]) 
            error ("can't compare many to many") 
         else 
            other.asInstanceOf[SEvent].name matches regex.pattern.pattern // TODO stupid matching :)))
      }
   case class OREvent   (le:Seq[Event], var name:String=null) extends Event with Many { 
      if(name==null) name = "OREvent"
         
      override def equals (other:Any) : Boolean = this ~= other
      override def ~= (other:Any) : Boolean = 
         if (other.isInstanceOf[Many]) error ("can't compare many to many") else le contains other
   }
   object NullEvent extends SEvent ("NULL")
   object AnyEvent extends SEvent ("ANY") {
      override def equals (other:Any) : Boolean = true
   }

   // -------------------------------- transitions
   
   trait Transition { 
      def applies (s:State, v:Event) : Option[State] 
      def + (cback : Cback) = TTransition (this, cback)
      def ++ (cback : =>Unit) = TTransition (this, wrap(cback))
      def wrap (f: =>Unit) (sm:StateMachine, t:Transition, e:Event) = f
   }

   case class TTransition (val orig:Transition, cback: Cback) extends Transition {
      override def applies (s:State, v:Event) = orig.applies (s, v)
   }
   
   case class ETransition (val from:State, val e:Event, val to:State) extends Transition {
      override def applies (s:State, v:Event) = if ((from ~= s) && (e ~= v)) Some(to) else None
   }
   
   object NullTransition extends Transition {
      override def applies (s:State, v:Event) = None
   }

   def apply (e:Int) : Event = IEvent(e)
   def apply (e:Event, s:State) : Transition = ETransition (NullState, e,s)
   def apply (c:Int, s:State) : Transition = ETransition (NullState, IEvent(c),s)
   
   case class X (s:String, i:Int, n:String)

   abstract class StateMachine {
      implicit def frei (i:Int) : Event = IEvent (i)
   
      implicit def fr1 (t:Tuple3[State, Event, State]) : Transition = 
         new ETransition (t._1, t._2, t._3)
      implicit def fr2 (t:Tuple3[String, Int, String]) : Transition = 
         new ETransition (state(t._1), t._2, state(t._3))
      implicit def fr3 (t:Tuple1[String]) : Transition =  
         { state(t._1); NullTransition }

      val mstates = razie.Mapi[String,State]()
      val transitions : Seq[Transition] // TODO optimize
      
      def start : State
      var currState : State = start
      
      def state (s:String) : State = mstates get s match {
         case Some (st) => st
         case None => { val x=new SState(s); mstates.put (s, x); x}
      }
     
      import razie.M._
      
      def move (e:Event) : StateMachine = {
         val o = razie.M.firstThat (transitions) ((t:Transition) => {t.applies (currState, e) != None})
         val m = o.map (_.applies(currState, e).getOrElse(NullState))
         val newState : State = m.getOrElse (NullState)
         
         newState match {
            case NullState => 
               logger trace "ERR-event not matched from \""+currState+"\" on event \""+e+"\""
            case _ => {
               logger trace "SM Moving from \""+currState+"\" to \""+newState+"\" on event \""+e+"\""
               // TODO keep history
               currState = newState
               o match {
                  case Some (t:Transition) => callcback (t, e)
                  case _ => 
               }
            }
         }
         
         this
      }
     
      def callcback (t:Transition, e:Event) {
         t match {
            case tt:TTransition => {
               callcback(tt.orig, e)
               tt.cback(this, tt, e) // post-order...don't move this up
            }
            case _ => 
         }
      }
      
      implicit def pfr6 (t:Tuple2[String,Event]) = new Pair (state(t._1), t._2)
      implicit def pfr1 (t:Tuple2[String,Int]) = new Pair (state(t._1), IEvent(t._2))
      implicit def pfr2 (t:Tuple2[String,Seq[Int]]) = new Pair (state(t._1), OREvent(t._2 map (IEvent(_))))
      implicit def pfr3 (t:Tuple2[String,Char]) = new Pair (state(t._1), IEvent(t._2))
      implicit def pfr4 (t:Tuple2[String,Event=>Boolean]) = new Pair (state(t._1), FEvent(t._2))
      implicit def pfr5 (t:Tuple2[Regex,Int]) = new Pair (REState(t._1), IEvent(t._2))
      implicit def pfr7 (t:Tuple2[Regex,Event]) = new Pair (REState(t._1), t._2)
//      implicit def pfr1 (t:Tuple1[String]) = new  (state(t._1), NullEvent(t._2))

   // ---------------- callbacks
      
    def echo  (s:String) (sm:StateMachine, t:Transition, e:Event) = 
       logger log "SM_LOG: event=" + e + " message=\""+s+"\""
    
    val stack = razie.Listi[Event]()
    def push (sm:StateMachine, t:Transition, e:Event) { e +=: stack }
    def pop (sm:StateMachine, t:Transition, e:Event) { stack remove 0 }
    def last = stack apply 0
    def resetStack = stack clear
    /** override to do something on reset */
    def reset { this.currState = start }
   }

   class Pair (s:State, e:Event) {
      def this (ss:String, i:Int)(implicit sm:StateMachine) = this (sm.state(ss), IEvent(i))
      def -> (x:State) : Transition = new ETransition (s, e, x)
      def -> (x:String)(implicit sm:StateMachine) : Transition = this -> sm.state(x)
   }
}

/** sample state machine */
class SampleSM extends razie.SM.StateMachine {
   import razie.SM._
   implicit val sm = this
   
   val states @ (s1, s2, s3) = ("s1", "s2", "s3")
   override def start = state("s1") // TODO there's an issue if i use s1 instead of "s1"
   
   override val transitions : Seq[Transition] = 
         (s1, 2)               -> s2 ::
         (s1, 0)               -> s1 :: // NOP 
         (s2, 1)               -> s1 + echo ("back to s1") :: // CR
         (s2, 3)               -> s3 ::
         (s3, Seq(4, 5, 6, 7)) -> s2 ::
         (s3, AnyEvent)        -> s1 :: 
         (s2, {_:Event=>true}) -> s2 + eatChar + echo ("s2 eats all others") :: // eating stuff
         (""".*""".r, -1)      -> s1 :: // some reset event applies to all states
          Nil

    def eatChar (sm:StateMachine, t:Transition, e:Event) = 
       println ("ate char: " + e.asInstanceOf[IEvent].i.toChar)
}

object SampleSMain extends Application {
   implicit def e (i:Int) = SM.IEvent(i)
   val sm = new SampleSM()
   sm move 2 move 1 move 3 move -1 move 2 move 3 move 6 move 59 
}