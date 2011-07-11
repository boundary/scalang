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

trait LinkListenable {
  @volatile var linkListeners : List[LinkListener] = Nil
  
  def addLinkListener(listener : LinkListener) {
    linkListeners = listener :: linkListeners
  }
  
  def notifyBreak(link : Link, reason : Any) {
    for (listener <- linkListeners) {
/*      println("notifying " + listener)*/
      listener.break(link, reason)
    }
  }
  
  def notifyDeliverLink(link : Link) {
    for (listener <- linkListeners) {
      listener.deliverLink(link)
    }
  }
}