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
package no.rmz.rmatch.compiler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A set of tests that verifies that the surface syntax parser for regular expressions is capable of
 * converting regexps with correct syntax to abstract regular expressions.
 */
@ExtendWith(MockitoExtension.class)
public class SurfaceRegexpParserTest {

  /**
   * A mock for the actual abstract regexp compiler that we will use to check that the right
   * invocations are sent to it.
   */
  @Mock AbstractRegexBuilder arb;

  /** The test article. */
  SurfaceRegexpParser instance;

  /** Setting up the test article. */
  @BeforeEach
  public void setUp() {
    instance = new SurfaceRegexpParser(arb);
  }

  /**
   * test that a single character can be parsed.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testParseSingleChar() throws Exception {
    instance.parse("a");
    verify(arb).addString("a");
  }

  /**
   * Test that a longish string can be parsed.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testParseLongishString() throws Exception {
    instance.parse("abanana");
    verify(arb).addString("abanana");
  }

  /**
   * Test that a simple set of alternatives can be parsed.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testParseSimpleAlternative() throws Exception {
    instance.parse("a|b");
    verify(arb).addString("a");
    verify(arb).separateAlternatives();
    verify(arb).addString("b");
  }

  /**
   * Test that an "alternative" consisting of a single alternative is correctly parsed.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testParseHalfSimpleAlternative() throws Exception {
    instance.parse("|b");
    verify(arb).addString("");
    verify(arb).separateAlternatives();
    verify(arb).addString("b");
  }

  /**
   * Test that an alternative consisting of three altenatives are correctly parsed.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testParseTripleAlternative() throws Exception {
    instance.parse("a|b|c");
    verify(arb, times(2)).separateAlternatives();
    verify(arb).addString("a");
    verify(arb).addString("b");
    verify(arb).addString("c");
  }

  /**
   * Check that a question mark is correctly parsed.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testQuestionMark() throws Exception {
    instance.parse("a?");
    verify(arb).addOptionalSingular();
  }

  /**
   * Test that a star is correctly parsed.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testStar() throws Exception {
    instance.parse("a*");
    verify(arb).addString("a");
    verify(arb).addOptionalZeroOrMulti();
  }

  /**
   * Test that a plus is correctly parsed.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testPlus() throws Exception {
    instance.parse("a+");
    verify(arb).addString("a");
    verify(arb).addOptionalOnceOrMulti();
  }

  /**
   * Test that a beginning of line (BOL) is parsed correctly.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testBOL() throws Exception {
    instance.parse("^a");
    verify(arb).addBeginningOfLine();
    verify(arb).addString("a");
  }

  /**
   * Test that end of line (EOL) is parsed correctly.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testEOL() throws Exception {
    instance.parse("a$");
    verify(arb).addString("a");
    verify(arb).addEndOfLine();
  }

  /**
   * Thest that the "any char" (dot) syntax is parsed correctly.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public void testAny() throws Exception {
    instance.parse("a.");
    verify(arb).addString("a");
    verify(arb).addAnyChar();
  }

  /**
   * Test that a simple character set is parsed correctly.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testSimpleCharSet() throws Exception {
    instance.parse("c[a]b");
    verify(arb).addString("c");
    verify(arb).startCharSet();
    verify(arb).addToCharSet("a");
    verify(arb).endCharSet();
    verify(arb).addString("b");
  }

  /**
   * Test that the character set inversion syntax works correctly.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testInvertedCharSet() throws Exception {
    instance.parse("[^a]");
    verify(arb).startCharSet();
    verify(arb).invertCharSet();
    verify(arb).addToCharSet("a");
    verify(arb).endCharSet();
  }

  /**
   * Check that the character range set in the char set syntax is parsed correctly.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testCharSetRange() throws Exception {
    instance.parse("[a-z]");
    verify(arb).startCharSet();
    verify(arb).addRangeToCharSet('a', 'z');
    verify(arb).endCharSet();
  }

  /**
   * Test a range within a character set.
   *
   * @throws Exception well it can happen.
   */
  @Test
  public final void testCharSetRangeInContext() throws Exception {
    instance.parse("[9a-z8]");
    verify(arb).startCharSet();
    verify(arb).addToCharSet("9");
    verify(arb).addRangeToCharSet('a', 'z');
    verify(arb).addToCharSet("8");
    verify(arb).endCharSet();
  }
}
