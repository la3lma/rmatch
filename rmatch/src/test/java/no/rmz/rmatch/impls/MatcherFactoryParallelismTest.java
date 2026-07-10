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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the hardware heuristic independently of the machine running the suite. */
public class MatcherFactoryParallelismTest {

  @Test
  void defaultParallelismScalesToCurrentHighEndProcessors() {
    assertEquals(1, MatcherFactory.defaultParallelismFor(1));
    assertEquals(1, MatcherFactory.defaultParallelismFor(2));
    assertEquals(6, MatcherFactory.defaultParallelismFor(4));
    assertEquals(288, MatcherFactory.defaultParallelismFor(192));
    assertEquals(576, MatcherFactory.defaultParallelismFor(384));
  }

  @Test
  void defaultParallelismHasAHighSafetyLimit() {
    assertEquals(1024, MatcherFactory.defaultParallelismFor(1024));
    assertEquals(1024, MatcherFactory.defaultParallelismFor(Integer.MAX_VALUE));
    assertThrows(IllegalArgumentException.class, () -> MatcherFactory.defaultParallelismFor(0));
  }
}
