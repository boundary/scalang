package scalang.node

import scalang._

trait LinkListener {
  def deliverLink(link : Link)
  
  def break(link : Link, reason : Any)
}