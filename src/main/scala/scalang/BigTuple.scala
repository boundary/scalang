package scalang

class BigTuple(val elements : Seq[Any]) extends Product {
  
  override def productElement(n : Int) = elements(n)
  
  override def productArity = elements.size
  
  override def canEqual(other : Any) : Boolean = {
    other match {
      case o : BigTuple => o.elements == elements
      case _ => false
    }
  }
  
  override def equals(other : Any) = canEqual(other)
}