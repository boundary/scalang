package scalang

import org.jboss.netty.buffer.ChannelBuffer

/**
 * Created with IntelliJ IDEA.
 * User: rjenkins
 * Date: 10/3/12
 * Time: 1:10 PM
 * To change this template use File | Settings | File Templates.
 */

trait TypeEncoder {
  def unapply(obj: Any): Option[Any]
  def encode(obj: Any, buffer: ChannelBuffer)

}
