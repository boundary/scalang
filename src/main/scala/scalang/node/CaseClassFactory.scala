package scalang.node

import scalang._
import overlock.atomicmap._
import scalang.util.UnderToCamel._

class CaseClassFactory(searchPrefixes : Seq[String], typeMappings : Map[String,Class[_]]) extends TypeFactory {
  //it's important to cache the negative side as well
  val classCache = AtomicMap.atomicNBHM[String,Option[Class[_]]]
  
  def createType(name : Symbol, arity : Int, reader : TermReader) : Option[Any] = {
    classCache.getOrElseUpdate(name.name, lookupClass(name.name)).flatMap { clazz =>
      tryCreateInstance(reader, clazz, arity)
    }
  }
  
  /**
   * Arity is the length of the tuple after the header
   */
  protected def tryCreateInstance(reader : TermReader, clazz : Class[_], arity : Int) : Option[Any] = {
    val candidates = for (constructor <- clazz.getConstructors if constructor.getParameterTypes.length == arity) yield {constructor}
    if (candidates.isEmpty) return None 
    reader.mark
    val parameters = for (i <- (0 until arity)) yield { reader.readTerm }
    val classes = parameters.map { case param : AnyRef =>
      param.getClass
    }
    candidates.find { constructor =>
      val params = constructor.getParameterTypes
      boxEquals(classes.toList, params.toList)
    }.flatMap { constructor =>
      try {
        Some(constructor.newInstance(parameters.asInstanceOf[Seq[Object]] : _*))
      } catch {
        case _ => None
      }
    }.orElse {
      reader.reset
      None
    }
  }
  
  protected def boxEquals(a : List[Class[_]], b : List[Class[_]]) : Boolean = {
    def scrubPrimitive(a : Class[_]) : Class[_] = a match {
      case java.lang.Byte.TYPE => classOf[java.lang.Byte]
      case java.lang.Short.TYPE => classOf[java.lang.Short]
      case java.lang.Integer.TYPE => classOf[java.lang.Integer]
      case java.lang.Long.TYPE => classOf[java.lang.Long]
      case java.lang.Boolean.TYPE => classOf[java.lang.Boolean]
      case java.lang.Character.TYPE => classOf[java.lang.Character]
      case java.lang.Float.TYPE => classOf[java.lang.Float]
      case java.lang.Double.TYPE => classOf[java.lang.Double]
      case x => x
    }
    
    (a,b) match {
      case (classA :: tailA, classB :: tailB) =>
        if (! (scrubPrimitive(classA) == scrubPrimitive(classB))) {
          return false
        }
        boxEquals(tailA, tailB)
      case (Nil, Nil) => true
      case _ => false
    }
      
  }
  
  
  protected def lookupClass(name : String) : Option[Class[_]] = {
    typeMappings.get(name) match {
      case Some(c) => Some(c)
      case None =>
        for (prefix <- searchPrefixes) {
          try {
            return Some(Class.forName(prefix + "." + name.underToCamel))
          } catch {
            case e : Exception => 
              e.printStackTrace
              Unit
          }
        }
        None
    }
  }
}