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
package no.rmz.rmatch.ordinaryuse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.LongAdder;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.RMatch;
import org.junit.jupiter.api.Test;

/**
 * Pins the documented behavioral contracts of {@link Matcher}: lifecycle (use-after-close),
 * idempotent close, and action-exception propagation. These tests exist so that the Javadoc
 * promises cannot silently drift away from the implementation.
 */
public class MatcherContractTest {

  // --- Lifecycle: everything except close() throws after close() ---

  @Test
  public void closedSingleMatcherRejectsUse() throws Exception {
    final Matcher m = RMatch.newSingleMatcher();
    m.add("a", (b, start, end) -> {});
    m.close();

    assertThrows(IllegalStateException.class, () -> m.match(RMatch.stringBuffer("a")));
    assertThrows(IllegalStateException.class, () -> m.add("b", (b, start, end) -> {}));
    assertThrows(IllegalStateException.class, () -> m.remove("a", (b, start, end) -> {}));
  }

  @Test
  public void closedPartitionedMatcherRejectsUse() throws Exception {
    final Matcher m = RMatch.newMatcher();
    m.add("a", (b, start, end) -> {});
    m.close();

    assertThrows(IllegalStateException.class, () -> m.match(RMatch.stringBuffer("a")));
    assertThrows(IllegalStateException.class, () -> m.add("b", (b, start, end) -> {}));
    assertThrows(IllegalStateException.class, () -> m.remove("a", (b, start, end) -> {}));
  }

  @Test
  public void closeIsIdempotent() {
    final Matcher single = RMatch.newSingleMatcher();
    single.close();
    assertDoesNotThrow(single::close);

    final Matcher partitioned = RMatch.newMatcher();
    partitioned.close();
    assertDoesNotThrow(partitioned::close);
  }

  // --- Action exceptions propagate out of match() ---

  @Test
  public void actionExceptionPropagatesFromSingleMatcher() throws Exception {
    try (Matcher m = RMatch.newSingleMatcher()) {
      m.add(
          "boom",
          (b, start, end) -> {
            throw new IllegalArgumentException("deliberate contract-test failure");
          });

      final IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> m.match(RMatch.stringBuffer("this goes boom now")));
      assertEquals("deliberate contract-test failure", thrown.getMessage());
    }
  }

  @Test
  public void matcherStaysUsableAfterActionException() throws Exception {
    try (Matcher m = RMatch.newSingleMatcher()) {
      final LongAdder matches = new LongAdder();
      m.add(
          "boom",
          (b, start, end) -> {
            throw new IllegalStateException("deliberate contract-test failure");
          });
      m.add("calm", (b, start, end) -> matches.increment());

      assertThrows(IllegalStateException.class, () -> m.match(RMatch.stringBuffer("boom")));

      // The matcher must still work for a subsequent scan that avoids the throwing pattern.
      m.match(RMatch.stringBuffer("calm waters"));
      assertTrue(matches.sum() > 0, "expected a match on the second, non-throwing scan");
    }
  }

  @Test
  public void partitionedMatcherRethrowsFirstActionFailureUnwrapped() throws Exception {
    try (Matcher m = RMatch.newMatcher()) {
      m.add(
          "boom",
          (b, start, end) -> {
            throw new IllegalArgumentException("deliberate contract-test failure");
          });

      final IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> m.match(RMatch.stringBuffer("this goes boom now")));
      assertEquals("deliberate contract-test failure", thrown.getMessage());
    }
  }
}
