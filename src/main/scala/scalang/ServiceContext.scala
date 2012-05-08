package scalang

trait ServiceContext[A <: Product] extends ProcessContext {
  def args : A
}

case class NoArgs()

object NoArgs extends NoArgs
