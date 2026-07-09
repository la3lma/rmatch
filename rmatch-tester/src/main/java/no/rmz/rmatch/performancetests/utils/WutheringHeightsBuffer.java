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
import no.rmz.rmatch.Buffer;

/**
 * A buffer implementation that delivers the full txt from Emily Bronte's novel "Wuthering Heights".
 */
public final class WutheringHeightsBuffer implements Buffer {

  public static final String LOCATION_OF_WUTHERING_HEIGHTS = "corpus/wuthr10.txt";

  private final StringSourceBuffer sb;

  public WutheringHeightsBuffer() {
    this(LOCATION_OF_WUTHERING_HEIGHTS);
  }

  public WutheringHeightsBuffer(final String filename) {
    final FileInhaler fileReader = new FileInhaler(new File(filename));
    sb = fileReader.inhaleAsStringBuffer();
  }

  @Override
  public boolean hasCharAt(final long pos) {
    return sb.hasCharAt(pos);
  }

  @Override
  public char charAt(final long pos) {
    return sb.charAt(pos);
  }

  @Override
  public String getString(final long start, final long stop) {
    return sb.getString(start, stop);
  }

  public int getLength() {
    return sb.getLength();
  }
}
