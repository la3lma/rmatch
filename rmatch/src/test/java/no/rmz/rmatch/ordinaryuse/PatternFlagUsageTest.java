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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.PatternFlag;
import no.rmz.rmatch.RMatch;
import org.junit.jupiter.api.Test;

/**
 * Exercises the flags-capable {@link Matcher#add(String, Set, no.rmz.rmatch.Action)} overload,
 * which fixes the 2.0 shape of the per-pattern flags API.
 */
public class PatternFlagUsageTest {

  @Test
  public void caseInsensitiveFlagMatchesAllCases() throws Exception {
    try (Matcher m = RMatch.newSingleMatcher()) {
      final Set<String> found = new TreeSet<>();
      m.add(
          "warn",
          Set.of(PatternFlag.CASE_INSENSITIVE),
          (b, start, end) -> found.add(b.getString(start, end)));

      m.match(RMatch.stringBuffer("warn Warn WARN warned"));

      assertTrue(found.contains("warn"), "lowercase form should match");
      assertTrue(found.contains("Warn"), "mixed-case form should match");
      assertTrue(found.contains("WARN"), "uppercase form should match");
    }
  }

  @Test
  public void nullFlagSetBehavesLikePlainAdd() throws Exception {
    try (Matcher m = RMatch.newSingleMatcher()) {
      final Set<String> found = new TreeSet<>();
      m.add("warn", (Set<PatternFlag>) null, (b, start, end) -> found.add(b.getString(start, end)));

      m.match(RMatch.stringBuffer("warn WARN"));

      assertEquals(
          Set.of("warn"), found, "null flags mean no flags; matching stays case-sensitive");
    }
  }

  @Test
  public void emptyFlagSetBehavesLikePlainAdd() throws Exception {
    try (Matcher m = RMatch.newSingleMatcher()) {
      final Set<String> found = new TreeSet<>();
      m.add("warn", Set.of(), (b, start, end) -> found.add(b.getString(start, end)));

      m.match(RMatch.stringBuffer("warn WARN"));

      assertEquals(Set.of("warn"), found, "without flags, matching stays case-sensitive");
    }
  }

  @Test
  public void flaggedAddWorksOnPartitionedMatcher() throws Exception {
    try (Matcher m = RMatch.newMatcher()) {
      final Set<String> found = new ConcurrentSkipListSet<>();
      m.add(
          "warn",
          Set.of(PatternFlag.CASE_INSENSITIVE),
          (b, start, end) -> found.add(b.getString(start, end)));

      m.match(RMatch.stringBuffer("warn Warn WARN"));

      assertEquals(Set.of("warn", "Warn", "WARN"), found);
    }
  }
}
