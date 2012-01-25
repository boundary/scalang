//
// Copyright 2011, Boundary
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package scalang.node

import org.jboss.netty
import netty.handler.codec.oneone._
import netty.channel._
import java.nio._
import java.math.BigInteger
import netty.buffer._
import scala.annotation.tailrec
import scalang._
import java.util.{Formatter, Locale}
import scalang.util.ByteArray
import scalang.util.CamelToUnder._
import com.codahale.logula.Logging
import util.ChannelBufferToHex._

class LoggingHandler extends SimpleChannelHandler with Logging {
  
  override def writeRequested(ctx : ChannelHandlerContext, e : MessageEvent) {
    val buffer = e.getMessage.asInstanceOf[ChannelBuffer]
    log.info("LOGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG WRIIIIIIIIIIIIIIIIIIIIIIIITE %s", buffer.toHex)
    super.writeRequested(ctx, e)
  }
}