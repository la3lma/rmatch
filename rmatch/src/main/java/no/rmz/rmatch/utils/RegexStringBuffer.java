/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may get a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rmz.rmatch.utils;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import no.rmz.rmatch.Buffer;
import no.rmz.rmatch.interfaces.LookaheadBuffer;

/**
 * {@link Buffer} implementation backed by a {@link String}.
 *
 * <p>This is the standard buffer implementation for application code. It keeps a cursor over the
 * supplied string and exposes matched text through {@link #getString(int, int)}. The input string
 * is immutable; cloned buffers share the same string but have independent cursor positions.
 */
public final class RegexStringBuffer implements LookaheadBuffer, Cloneable {

  /** A string containing the entire content of the buffer. */
  private final String str;

  /** The current position of the buffer. */
  private int currentPos;

  /** The current character. */
  private char currentChar;

  /** Monitor used when synchronizing access to this instance. */
  private final Object monitor = new Object();

  /**
   * Create a buffer over the supplied string.
   *
   * <p>The initial cursor position is before the first character. The first call to {@link
   * #getNext()} returns the character at offset {@code 0}.
   *
   * @param str input text to scan
   */
  public RegexStringBuffer(final String str) {
    this.str = checkNotNull(str);
    currentPos = -1;
  }

  /**
   * Clone the other string buffer.
   *
   * @param aThis the buffer to clone.
   */
  private RegexStringBuffer(final RegexStringBuffer aThis) {
    this.str = aThis.str;
    this.currentPos = aThis.currentPos;
    this.currentChar = aThis.currentChar;
  }

  /**
   * Return whether another character can be consumed.
   *
   * @return {@code true} if {@link #getNext()} can advance the cursor
   */
  @Override
  public boolean hasNext() {
    synchronized (monitor) {
      int lastPos = getLength() - 2;
      return currentPos <= lastPos;
    }
  }

  /** Advance the position pointer by one and update the currentChar value. */
  private void progress() {
    synchronized (monitor) {
      currentPos += 1;
      currentChar = str.charAt(currentPos);
    }
  }

  /**
   * Advance the cursor and return the next character.
   *
   * @return next character in the string
   */
  @Override
  public Character getNext() {
    synchronized (monitor) {
      progress();
      return currentChar;
    }
  }

  /**
   * Return the next character without advancing the cursor.
   *
   * @return next character, or {@code null} at end of input
   */
  @Override
  public Character peek() {
    synchronized (monitor) {
      final int nextPos = currentPos + 1;
      if (nextPos < str.length()) {
        return str.charAt(nextPos);
      }
      return null;
    }
  }

  /**
   * Return the current zero-based cursor position.
   *
   * @return current cursor position, or {@code -1} before scanning starts
   */
  @Override
  public int getCurrentPos() {
    synchronized (monitor) {
      return currentPos;
    }
  }

  /**
   * Return the total length of the backing string.
   *
   * @return number of characters in the backing string
   */
  public int getLength() {
    synchronized (monitor) {
      return str.length();
    }
  }

  /**
   * Return a substring from the backing string.
   *
   * <p>The {@code stop} argument is exclusive, just like {@link String#substring(int, int)}.
   *
   * @param start zero-based inclusive start offset
   * @param stop zero-based exclusive stop offset
   * @return text in the half-open range {@code [start, stop)}
   */
  @Override
  public String getString(final int start, final int stop) {
    synchronized (monitor) {
      return str.substring(start, stop);
    }
  }

  /**
   * Return the suffix from {@code start} to the end of the backing string.
   *
   * @param start zero-based inclusive start offset
   * @return text from {@code start} through the end of input
   */
  public String getCurrentRestString(final int start) {
    synchronized (monitor) {
      int end = getLength();
      return getString(start, end);
    }
  }

  @Override
  public String toString() {
    synchronized (monitor) {
      return "[RegexStringBuffer currentPos = " + currentPos + ". str = " + str + "]";
    }
  }

  /**
   * Return an independent cursor over the same backing string.
   *
   * @return cloned buffer with the same content and current position
   */
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Buffer clone() {
    return new RegexStringBuffer(this);
  }
}
