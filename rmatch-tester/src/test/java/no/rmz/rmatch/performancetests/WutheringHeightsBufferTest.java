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
package no.rmz.rmatch.performancetests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;
import org.junit.jupiter.api.Test;

/** Test reading the text of wuthering heights. */
public final class WutheringHeightsBufferTest {

  /** The number of characters in the text. */
  private static final int MINIMUM_NUMBER_OF_CHARS_IN_WUTHERING_HEIGHTS = 662077;

  /** Test reading the text and then check that we got all the characters. */
  @Test
  public void testInhale() {
    final WutheringHeightsBuffer whb = new WutheringHeightsBuffer();
    final int len = whb.getLength();

    assertEquals(MINIMUM_NUMBER_OF_CHARS_IN_WUTHERING_HEIGHTS, len);
  }
}
