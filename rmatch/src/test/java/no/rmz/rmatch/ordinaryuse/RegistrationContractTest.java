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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import no.rmz.rmatch.Action;
import no.rmz.rmatch.Buffer;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.RMatch;
import org.junit.jupiter.api.Test;

/** Contract tests for the register-then-match lifecycle and callback identity. */
public class RegistrationContractTest {

  @Test
  void firstMatchFreezesSingleMatcherRegistrations() throws Exception {
    assertRegistrationFreezes(RMatch::newSingleMatcher);
  }

  @Test
  void firstMatchFreezesPartitionedMatcherRegistrations() throws Exception {
    assertRegistrationFreezes(() -> RMatch.newMatcher(4));
  }

  @Test
  void emptyFirstScanStillFreezesRegistration() throws Exception {
    try (Matcher matcher = RMatch.newSingleMatcher()) {
      matcher.match(RMatch.stringBuffer(""));

      assertThrows(
          IllegalStateException.class, () -> matcher.add("abc", (buffer, start, end) -> {}));
    }
  }

  @Test
  void failedFirstScanStillFreezesRegistration() throws Exception {
    try (Matcher matcher = RMatch.newSingleMatcher()) {
      matcher.add(
          "boom",
          (buffer, start, end) -> {
            throw new IllegalStateException("deliberate failure");
          });

      assertThrows(IllegalStateException.class, () -> matcher.match(RMatch.stringBuffer("boom")));
      assertThrows(
          IllegalStateException.class, () -> matcher.add("abc", (buffer, start, end) -> {}));
    }
  }

  @Test
  void registeringTheSameActionInstanceTwiceIsIdempotent() throws Exception {
    final LongAdder calls = new LongAdder();
    final Action action = (buffer, start, end) -> calls.increment();

    try (Matcher matcher = RMatch.newSingleMatcher()) {
      matcher.add("abc", action);
      matcher.add("abc", action);
      matcher.match(RMatch.stringBuffer("abc"));
    }

    assertEquals(1L, calls.sum());
  }

  @Test
  void distinctActionInstancesRemainDistinctEvenWhenTheyCompareEqual() throws Exception {
    final LongAdder calls = new LongAdder();

    try (Matcher matcher = RMatch.newSingleMatcher()) {
      matcher.add("abc", new EqualAction(calls));
      matcher.add("abc", new EqualAction(calls));
      matcher.match(RMatch.stringBuffer("abc"));
    }

    assertEquals(2L, calls.sum());
  }

  @Test
  void publicMatcherSurfaceDoesNotOfferExperimentalRemoval() {
    assertFalse(
        Arrays.stream(Matcher.class.getMethods())
            .anyMatch(method -> method.getName().equals("remove")));
  }

  private static void assertRegistrationFreezes(final Supplier<Matcher> factory) throws Exception {
    try (Matcher matcher = factory.get()) {
      matcher.add("abc", (buffer, start, end) -> {});
      matcher.match(RMatch.stringBuffer("abc"));

      assertThrows(
          IllegalStateException.class, () -> matcher.add("xyz", (buffer, start, end) -> {}));
    }
  }

  private static final class EqualAction implements Action {
    private final LongAdder calls;

    private EqualAction(final LongAdder calls) {
      this.calls = calls;
    }

    @Override
    public void performMatch(final Buffer buffer, final long start, final long end) {
      calls.increment();
    }

    @Override
    public boolean equals(final Object other) {
      return other instanceof EqualAction;
    }

    @Override
    public int hashCode() {
      return EqualAction.class.hashCode();
    }
  }
}
