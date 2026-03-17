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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import no.rmz.rmatch.interfaces.NodeStorage;
import no.rmz.rmatch.interfaces.Regexp;
import org.junit.jupiter.api.Test;

final class FastPathMatchEnginePrefilterThresholdTest {

  @Test
  void doesNotActivatePrefilterBelowThreshold() throws Exception {
    final String oldPrefilter = System.getProperty("rmatch.prefilter");
    try {
      System.setProperty("rmatch.prefilter", "aho");

      final FastPathMatchEngine engine = new FastPathMatchEngine(mock(NodeStorage.class));
      engine.configurePrefilter(patterns(4999), flags(4999), regexpMappings(4999));

      assertNull(readPrefilter(engine));
    } finally {
      restoreSystemProperty("rmatch.prefilter", oldPrefilter);
    }
  }

  @Test
  void activatesPrefilterAtThreshold() throws Exception {
    final String oldPrefilter = System.getProperty("rmatch.prefilter");
    try {
      System.setProperty("rmatch.prefilter", "aho");

      final FastPathMatchEngine engine = new FastPathMatchEngine(mock(NodeStorage.class));
      engine.configurePrefilter(patterns(5000), flags(5000), regexpMappings(5000));

      assertNotNull(readPrefilter(engine));
    } finally {
      restoreSystemProperty("rmatch.prefilter", oldPrefilter);
    }
  }

  private static Object readPrefilter(final FastPathMatchEngine engine) throws Exception {
    final Field field = FastPathMatchEngine.class.getDeclaredField("prefilter");
    field.setAccessible(true);
    return field.get(engine);
  }

  private static Map<Integer, String> patterns(final int count) {
    final Map<Integer, String> patterns = new HashMap<>();
    for (int i = 0; i < count; i++) {
      patterns.put(i, "literalPattern" + i);
    }
    return patterns;
  }

  private static Map<Integer, Integer> flags(final int count) {
    final Map<Integer, Integer> flags = new HashMap<>();
    for (int i = 0; i < count; i++) {
      flags.put(i, 0);
    }
    return flags;
  }

  private static Map<String, Regexp> regexpMappings(final int count) {
    final Map<String, Regexp> mappings = new HashMap<>();
    for (int i = 0; i < count; i++) {
      mappings.put("literalPattern" + i, mock(Regexp.class));
    }
    return mappings;
  }

  private static void restoreSystemProperty(final String key, final String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
