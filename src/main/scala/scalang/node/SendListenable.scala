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

trait SendListenable {
  @volatile var sendListeners : List[SendListener] = Nil

  def addSendListener(listener : SendListener) {
    sendListeners = listener :: sendListeners
  }

  def notifySend(pid : Pid, msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(pid, msg)
    }
  }

  def notifySend(name : Symbol, msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(name, msg)
    }
  }

  def notifySend(dest : (Symbol,Symbol), from : Pid, msg : Any) : Any = {
    for (l <- sendListeners) {
      l.handleSend(dest, from, msg)
    }
  }

}
