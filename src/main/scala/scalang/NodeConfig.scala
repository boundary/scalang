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
package scalang

import util._
import org.jboss.netty.buffer.ChannelBuffer

case class NodeConfig(
  poolFactory : ThreadPoolFactory = new DefaultThreadPoolFactory,
  clusterListener : Option[ClusterListener] = None,
  typeFactory : TypeFactory = NoneTypeFactory,
  typeEncoder: TypeEncoder = NoneTypeEncoder,
  typeDecoder : TypeDecoder = NoneTypeDecoder,
  tickTime : Int = 60)

object NoneTypeFactory extends TypeFactory {
  def createType(name : Symbol, arity : Int, reader : TermReader) = None
}

object NoneTypeEncoder extends TypeEncoder {
  def unapply(obj: Any) = { None }
  def encode(obj: Any, buffer: ChannelBuffer) {}
}

object NoneTypeDecoder extends TypeDecoder {
  def unapply(typeOrdinal : Int) : Option[Int] = { None }
  def decode(typeOrdinal : Int, buffer : ChannelBuffer) : Any = {}
}
