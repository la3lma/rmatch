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
package no.rmz.rmatch.performancetests.utils;

import java.io.File;
import no.rmz.rmatch.interfaces.Buffer;

/**
 * A buffer implementation that delivers the full txt from Emily Bronte's novel "Wuthering Heights".
 */
public final class WutheringHeightsBuffer implements Buffer, Cloneable {

  /**
   * The location in the local filesystem, relative to the rmatch project, where the Wuthering
   * Heights text is stored.
   */
  public static final String LOCATION_OF_WUTHERING_HEIGHTS = "corpus/wuthr10.txt";

  /**
   * A RegexStringBuffer instance that holds the entire text from Emily Bronte's novel "Wuthering
   * Heights".
   */
  private final StringSourceBuffer sb;

  /** Create a new buffer and inhale the content from the file corpus/wuthr10.txt. */
  public WutheringHeightsBuffer() {
    this(LOCATION_OF_WUTHERING_HEIGHTS);
  }

  /** Create a new buffer and inhale the content from some file. */
  public WutheringHeightsBuffer(final String filename) {
    final FileInhaler fileReader = new FileInhaler(new File(filename));
    sb = fileReader.inhaleAsStringBuffer();
  }

  /**
   * Set the position to read the next character from to be i.
   *
   * @param i the next position to read from
   */
  public void setCurrentPos(final int i) {
    sb.setCurrentPos(i);
  }

  @Override
  public boolean hasNext() {
    return sb.hasNext();
  }

  @Override
  public String getString(final int start, final int stop) {
    return sb.getString(start, stop);
  }

  @Override
  public Character getNext() {
    return sb.getNext();
  }

  /**
   * Get the length of the buffer.
   *
   * @return length of buffer.
   */
  public int getLength() {
    return sb.getLength();
  }

  @Override
  public String getCurrentRestString() {
    return sb.getCurrentRestString();
  }

  /**
   * Get the substring from position 'pos' going on to the end of the text.
   *
   * @param pos the start position for the rest-string.
   * @return a string.
   */
  public String getCurrentRestString(final int pos) {
    return sb.getCurrentRestString(pos);
  }

  @Override
  public int getCurrentPos() {
    return sb.getCurrentPos();
  }

  @Override
  public Buffer clone() {
    return sb.clone();
  }
}
