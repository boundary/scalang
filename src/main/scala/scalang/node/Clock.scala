package scalang.node

trait Clock {
  def currentTimeMillis : Long
}

class SystemClock extends Clock {
  override def currentTimeMillis = System.currentTimeMillis
}
