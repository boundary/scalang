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

import scalang._

case class LinkMessage(from : Pid, to : Pid)

case class SendMessage(to : Pid, msg : Any)

case class ExitMessage(from : Pid, to : Pid, reason : Any)

case class Exit2Message(from : Pid, to : Pid, reason : Any)

case class UnlinkMessage(from : Pid, to : Pid)

case class RegSend(from : Pid, to : Symbol, msg : Any)

case object Tick

case object Tock

//must implement trace tags later

case class MonitorMessage(monitoring : Pid, monitored : Any, ref : Reference)

case class DemonitorMessage(monitoring : Pid, monitored : Any, ref : Reference)

case class MonitorExitMessage(monitored : Any, monitoring : Pid, ref : Reference, reason : Any)

class DistributedProtocolException(msg : String) extends Exception(msg)
