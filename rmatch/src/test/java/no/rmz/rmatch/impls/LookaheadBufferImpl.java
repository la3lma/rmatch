/**
 * Copyright 2026. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.LookaheadBuffer;

public class LookaheadBufferImpl implements LookaheadBuffer, Cloneable {

  private final Object lock = new Object();
  private final Buffer buffer;
  private Character peekedChar;
  private boolean havePeeked;

  public LookaheadBufferImpl(final Buffer buffer) {
    this.buffer = buffer;
    this.havePeeked = false;
    this.peekedChar = null;
  }

  @Override
  @Deprecated
  public String getCurrentRestString() {
    synchronized (lock) {
      return buffer.getCurrentRestString();
    }
  }

  @Override
  public String getString(int start, int stop) {
    synchronized (lock) {
      return buffer.getString(start, stop);
    }
  }

  @Override
  public boolean hasNext() {
    synchronized (lock) {
      return buffer.hasNext();
    }
  }

  @Override
  public Character getNext() {
    synchronized (lock) {
      if (havePeeked) {
        havePeeked = false;
        return peekedChar;
      } else {
        return buffer.getNext();
      }
    }
  }

  @Override
  public int getCurrentPos() {
    synchronized (lock) {
      return buffer.getCurrentPos();
    }
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Buffer clone() {
    synchronized (lock) {
      return new LookaheadBufferImpl(buffer.clone());
    }
  }

  @Override
  public Character peek() {
    synchronized (lock) {
      if (!this.havePeeked) {
        this.havePeeked = true;
        if (buffer.hasNext()) {
          this.peekedChar = buffer.getNext();
        } else {
          this.peekedChar = null;
        }
      }
      return peekedChar;
    }
  }
}
