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
import scala.annotation.tailrec
import java.util.concurrent._
import atomic._
import locks._
import java.util.Arrays

class ReferenceCounter(name : Symbol, creation : Int) {
  @volatile var refid = Array(0,0,0)
  val lock = new ReentrantLock

  protected def increment {
    val newRefid = Arrays.copyOf(refid, 3)
    newRefid(0) += 1
    if (newRefid(0) > 0x3ffff) {
      newRefid(0) = 0
      newRefid(1) += 1
      if (newRefid(1) == 0) {
        newRefid(2) += 1
      }
    }
    refid = newRefid
  }

  def makeRef : Reference = {
    lock.lock
    try {
      val ref = Reference(name, refid, creation)
      increment
      ref
    } finally {
      lock.unlock
    }
  }
}
