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
