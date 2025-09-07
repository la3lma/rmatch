/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
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
package no.rmz.rmatch.performancetests.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import no.rmz.rmatch.interfaces.Buffer;

/** An implementation of the Buffer interface, that holds all of the input as a String. */
public final class StringSourceBuffer implements Buffer, Cloneable {

  /** A string containing the entire content of the buffer. */
  private final String str;

  /** The current position of the buffer. */
  private int currentPos;

  /** The current character. */
  private char currentChar;

  /** A monitor instance used when synchronizing access to this instance. */
  private final Object monitor = new Object();

  /**
   * Create a new instance, with content as a string.
   *
   * @param str The string we will return character by character.
   */
  public StringSourceBuffer(final String str) {
    this.str = checkNotNull(str);
    currentPos = -1;
  }

  /**
   * Clone the other string buffer.
   *
   * @param aThis the buffer to clone.
   */
  private StringSourceBuffer(final StringSourceBuffer aThis) {
    this.str = aThis.str;
    this.currentPos = aThis.currentPos;
    this.currentChar = aThis.currentChar;
  }

  /**
   * Set the current position to be somewhere in the string.
   *
   * @param pos the pos to set currentPos to.
   */
  public void setCurrentPos(final int pos) {
    synchronized (monitor) {
      assert pos > 0 && pos < str.length();
      currentPos = pos;
    }
  }

  @Override
  public boolean hasNext() {
    synchronized (monitor) {
      int lastPos = getLength() - 2;
      return currentPos <= lastPos;
    }
  }

  /** Advance the position pointer by one, and update the currentChar value. */
  private void progress() {
    synchronized (monitor) {
      currentPos += 1;
      currentChar = str.charAt(currentPos);
    }
  }

  @Override
  public Character getNext() {
    synchronized (monitor) {
      progress();
      return currentChar;
    }
  }

  @Override
  public int getCurrentPos() {
    synchronized (monitor) {
      return currentPos;
    }
  }

  /**
   * Get the length of the current string.
   *
   * @return the length of the string.
   */
  public int getLength() {
    synchronized (monitor) {
      return str.length();
    }
  }

  @Override
  public String getString(final int start, final int stop) {
    synchronized (monitor) {
      return str.substring(start, stop);
    }
  }

  public String getString() {
    return str;
  }

  /**
   * Get the string from the start position to the end.
   *
   * @param start start position.
   * @return the string from the start position to the end.
   */
  public String getCurrentRestString(final int start) {
    synchronized (monitor) {
      int end = getLength();
      return getString(start, end);
    }
  }

  @Override
  public String getCurrentRestString() {
    synchronized (monitor) {
      return getCurrentRestString(getCurrentPos() + 1);
    }
  }

  @Override
  public String toString() {
    synchronized (monitor) {
      return "[StringBuffer currentPos = " + currentPos + ". str = " + str + "]";
    }
  }

  @Override
  public Buffer clone() {
    return new StringSourceBuffer(this);
  }
}
