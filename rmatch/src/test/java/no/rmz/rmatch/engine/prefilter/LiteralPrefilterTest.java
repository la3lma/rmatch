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
package no.rmz.rmatch.engine.prefilter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Tests for the LiteralPrefilter class.
 *
 * <p>Verifies correct literal extraction from regex patterns including handling of anchored
 * prefixes, case-insensitive patterns, quoted blocks, character classes, and patterns with no
 * extractable literals.
 */
public class LiteralPrefilterTest {

  @Test
  public void testExtractsLongestRun() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(1, "a(?:b|cd)e", 0);
    assertTrue(hint.isPresent());
    assertEquals("cd", hint.get().literal());
    assertEquals(1, hint.get().patternId());
    assertFalse(hint.get().anchoredPrefix());
    assertFalse(hint.get().caseInsensitive());
  }

  @Test
  public void testHandlesQuotedBlocks() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(2, "\\Qhttp://\\E[a-z]+\\.com", 0);
    assertTrue(hint.isPresent());
    assertEquals("http://", hint.get().literal());
    assertEquals(2, hint.get().patternId());
    assertFalse(hint.get().anchoredPrefix());
    assertFalse(hint.get().caseInsensitive());
  }

  @Test
  public void testIgnoresCharClasses() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(3, "foo[a-z]+bar", 0);
    assertTrue(hint.isPresent());
    // Should extract either "foo" or "bar" - both are length 3, implementation picks first
    assertTrue("foo".equals(hint.get().literal()) || "bar".equals(hint.get().literal()));
    assertEquals(3, hint.get().patternId());
  }

  @Test
  public void testBreaksOnAlternation() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(4, "abc|def", 0);
    assertTrue(hint.isPresent());
    // Should extract either "abc" or "def" - both are length 3
    assertTrue("abc".equals(hint.get().literal()) || "def".equals(hint.get().literal()));
    assertEquals(4, hint.get().patternId());
  }

  @Test
  public void testPrefersAnchoredWhenApplicable() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(5, "^foo.*bar", 0);
    assertTrue(hint.isPresent());
    // Should prefer "foo" due to anchoring, even though "bar" might be same length
    assertEquals("foo", hint.get().literal());
    assertEquals(5, hint.get().patternId());
    assertTrue(hint.get().anchoredPrefix());
  }

  @Test
  public void testCaseInsensitiveFlagAffectsHint() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(6, "FOO", Pattern.CASE_INSENSITIVE);
    assertTrue(hint.isPresent());
    assertEquals("FOO", hint.get().literal());
    assertEquals(6, hint.get().patternId());
    assertTrue(hint.get().caseInsensitive());
  }

  @Test
  public void testReturnsEmptyWhenNoLiteral() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(7, ".*", 0);
    assertFalse(hint.isPresent());
  }

  @Test
  public void testReturnsEmptyForTooShortLiteral() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(8, "a", 0);
    assertFalse(hint.isPresent());
  }

  @Test
  public void testReturnsEmptyForOnlyMetaChars() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(9, "^$", 0);
    assertFalse(hint.isPresent());
  }

  @Test
  public void testHandlesEscapedCharacters() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(10, "test\\.com", 0);
    assertTrue(hint.isPresent());
    assertEquals("test.com", hint.get().literal());
    assertEquals(10, hint.get().patternId());
  }

  @Test
  public void testHandlesComplexPattern() {
    final Optional<LiteralHint> hint =
        LiteralPrefilter.extract(11, "^(https?://)?(www\\.)?example\\.com(/.*)?$", 0);
    assertTrue(hint.isPresent());
    // Should find a literal substring, likely "example.com" or similar
    assertNotNull(hint.get().literal());
    assertTrue(hint.get().literal().length() >= 2);
    assertEquals(11, hint.get().patternId());
  }

  @Test
  public void testHandlesAlternationWithDifferentLengths() {
    final Optional<LiteralHint> hint = LiteralPrefilter.extract(12, "short|verylongpattern", 0);
    assertTrue(hint.isPresent());
    // Should prefer the longer literal
    assertEquals("verylongpattern", hint.get().literal());
    assertEquals(12, hint.get().patternId());
  }

  @Test
  public void testMinLiteralLengthKillSwitch() {
    final String oldMinLength = System.getProperty("rmatch.prefilter.literal.minLength");
    try {
      System.setProperty("rmatch.prefilter.literal.minLength", "4");
      final Optional<LiteralHint> hint = LiteralPrefilter.extract(13, "abc|de", 0);
      assertFalse(hint.isPresent(), "minLength=4 should suppress 3-char literals");
    } finally {
      restoreSystemProperty("rmatch.prefilter.literal.minLength", oldMinLength);
    }
  }

  @Test
  public void testCommonWordPenaltyKillSwitch() {
    final String oldPenaltyEnabled =
        System.getProperty("rmatch.prefilter.literal.commonWordPenalty.enabled");
    final String oldPenaltyFactor =
        System.getProperty("rmatch.prefilter.literal.commonWordPenalty.factor");
    try {
      System.setProperty("rmatch.prefilter.literal.commonWordPenalty.enabled", "true");
      System.setProperty("rmatch.prefilter.literal.commonWordPenalty.factor", "0.5");
      final Optional<LiteralHint> penalized = LiteralPrefilter.extract(14, "there|zxq", 0);
      assertTrue(penalized.isPresent());
      assertEquals(
          "zxq",
          penalized.get().literal(),
          "Common-word penalty should demote 'there' below 'zxq'");

      System.setProperty("rmatch.prefilter.literal.commonWordPenalty.enabled", "false");
      final Optional<LiteralHint> unpenalized = LiteralPrefilter.extract(15, "there|zxq", 0);
      assertTrue(unpenalized.isPresent());
      assertEquals(
          "there",
          unpenalized.get().literal(),
          "Disabling penalty should restore raw selectivity ordering");
    } finally {
      restoreSystemProperty(
          "rmatch.prefilter.literal.commonWordPenalty.enabled", oldPenaltyEnabled);
      restoreSystemProperty("rmatch.prefilter.literal.commonWordPenalty.factor", oldPenaltyFactor);
    }
  }

  private static void restoreSystemProperty(final String key, final String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
