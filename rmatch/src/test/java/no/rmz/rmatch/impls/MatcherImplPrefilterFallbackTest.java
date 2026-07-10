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
package no.rmz.rmatch.impls;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import no.rmz.rmatch.Buffer;
import no.rmz.rmatch.RegexpParserException;
import org.junit.jupiter.api.Test;

/**
 * Ensures prefilter-enabled engines keep matching semantics for generic Buffer implementations.
 *
 * <p>This protects against false negatives when the buffer is not {@code
 * no.rmz.rmatch.utils.RegexStringBuffer}.
 */
public final class MatcherImplPrefilterFallbackTest {

  @Test
  public void testFastPathPrefilterFallsBackForGenericBuffer() throws RegexpParserException {
    final String oldEngine = System.getProperty("rmatch.engine");
    final String oldPrefilter = System.getProperty("rmatch.prefilter");

    try {
      System.setProperty("rmatch.engine", "fastpath");
      System.setProperty("rmatch.prefilter", "aho");

      final MatcherImpl matcher = new MatcherImpl();
      final AtomicInteger matchCount = new AtomicInteger(0);
      matcher.add("frost", (buffer, start, end) -> matchCount.incrementAndGet());
      matcher.match(new GenericTestBuffer("winter frost and hoarfrost"));

      assertTrue(matchCount.get() > 0, "Expected at least one match with prefilter enabled");
    } finally {
      restoreSystemProperty("rmatch.engine", oldEngine);
      restoreSystemProperty("rmatch.prefilter", oldPrefilter);
    }
  }

  private static void restoreSystemProperty(final String key, final String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  /** Minimal Buffer implementation for regression testing non-RegexStringBuffer input. */
  private static final class GenericTestBuffer implements Buffer {
    private final String text;

    private GenericTestBuffer(final String text) {
      this.text = text;
    }

    @Override
    public boolean hasCharAt(final long pos) {
      return pos >= 0 && pos < text.length();
    }

    @Override
    public char charAt(final long pos) {
      return text.charAt(Math.toIntExact(pos));
    }

    @Override
    public String getString(final long start, final long stop) {
      return text.substring((int) Math.max(0, start), (int) Math.min(stop, text.length()));
    }
  }
}
