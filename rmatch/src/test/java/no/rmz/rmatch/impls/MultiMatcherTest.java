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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import no.rmz.rmatch.Action;
import no.rmz.rmatch.RegexpParserException;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for MultiMatcher's failure handling and cross-thread determinism.
 *
 * <p>Historical context ("skidding"): an earlier bug family involved match counts being read while
 * worker threads were still running, and later a benchmark harness lost counter updates by
 * incrementing a plain int from concurrent action callbacks. These tests pin down the engine-side
 * guarantees: a failing partition must fail match() (not hang it), and identical inputs must
 * produce identical match counts run after run when the action counts thread-safely.
 */
public class MultiMatcherTest {

  private static final int PARTITIONS = 4;

  private static MultiMatcher newMatcher() {
    return new MultiMatcher(
        PARTITIONS, new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
  }

  /**
   * A partition whose action throws must cause match() to throw, not hang forever waiting for a
   * CountDownLatch that will never reach zero. The timeout guards against a hang regression.
   */
  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void actionFailureInPartitionPropagatesInsteadOfHanging()
      throws RegexpParserException, InterruptedException {
    final MultiMatcher matcher = newMatcher();
    try {
      matcher.add(
          "boom",
          (b, start, end) -> {
            throw new IllegalStateException("deliberate test failure");
          });

      final RuntimeException thrown =
          assertThrows(
              IllegalStateException.class,
              () -> matcher.match(new RegexStringBuffer("kaboom goes the boom")));
      assertEquals("deliberate test failure", thrown.getMessage());
    } finally {
      matcher.close();
    }
  }

  /** All partitions must still complete (and count down) when one of them fails. */
  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void healthyPartitionsStillMatchWhenAnotherPartitionFails()
      throws RegexpParserException, InterruptedException {
    final MultiMatcher matcher = newMatcher();
    try {
      final LongAdder healthyMatches = new LongAdder();
      // Several patterns so that the hash-based partitioning spreads them around,
      // making it likely the failing pattern shares the run with healthy ones.
      matcher.add("boom", healthyAction(healthyMatches));
      matcher.add("kaboom", healthyAction(healthyMatches));
      matcher.add("goes", healthyAction(healthyMatches));
      matcher.add(
          "the",
          (b, start, end) -> {
            throw new IllegalStateException("deliberate test failure");
          });

      assertThrows(
          IllegalStateException.class,
          () -> matcher.match(new RegexStringBuffer("kaboom goes the boom")));

      // A second match on a fresh matcher must be unaffected (no stuck latch,
      // no poisoned executor).
      final MultiMatcher fresh = newMatcher();
      try {
        final LongAdder counter = new LongAdder();
        fresh.add("boom", healthyAction(counter));
        fresh.match(new RegexStringBuffer("kaboom goes the boom"));
        assertTrue(counter.sum() > 0, "expected at least one match for 'boom'");
      } finally {
        fresh.close();
      }
    } finally {
      matcher.close();
    }
  }

  /**
   * Identical input must produce identical match counts, run after run, when the shared action
   * aggregates thread-safely. This is the engine-side determinism guarantee behind the Action
   * thread-safety contract.
   */
  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  public void matchCountsAreDeterministicAcrossRuns()
      throws RegexpParserException, InterruptedException {
    final String[] words = {
      "cat", "dog", "bird", "fish", "cow", "horse", "sheep", "goat",
      "red", "green", "blue", "black", "white", "brown", "gray", "pink"
    };
    final StringBuilder corpus = new StringBuilder();
    for (int i = 0; i < 2000; i++) {
      corpus.append(words[i % words.length]).append(' ');
    }
    final String input = corpus.toString();

    Long expected = null;
    for (int run = 0; run < 3; run++) {
      final MultiMatcher matcher = newMatcher();
      try {
        final LongAdder counter = new LongAdder();
        for (final String word : words) {
          matcher.add(word, healthyAction(counter));
        }
        matcher.match(new RegexStringBuffer(input));
        final long total = counter.sum();
        assertTrue(total > 0, "expected matches in run " + run);
        if (expected == null) {
          expected = total;
        } else {
          assertEquals(
              expected.longValue(),
              total,
              "match count must be identical across identical runs (run " + run + ")");
        }
      } finally {
        matcher.close();
      }
    }
  }

  private static Action healthyAction(final LongAdder counter) {
    return (b, start, end) -> counter.increment();
  }
}
