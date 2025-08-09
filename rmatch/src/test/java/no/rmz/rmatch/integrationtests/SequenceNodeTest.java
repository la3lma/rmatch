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
package no.rmz.rmatch.integrationtests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.impls.RegexpImpl;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.mockedcompiler.CharSequenceCompiler;
import no.rmz.rmatch.testutils.GraphDumper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * This is a basic test of two or more NDFA nodes after one another encoding a sequence. It runs
 * with handcrafted nodes (no compilers), and tests tha basic matcher algorithm. If this doesn't
 * work nothing will.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SequenceNodeTest {

  /** A string containing the text "ab". */
  static final String AB_STRING = "ab";

  /** A string containing the text "ac". */
  static final String AC_STRING = "ac";

  /** A string contaning the text "ab ac". */
  static final String ABAC_STRING = AB_STRING + " " + AC_STRING;

  /** The start position of the "ab" part in the ABAC string. */
  static final int AB_START = 0;

  /** THe end position of the "ab" part of the ABAC string. */
  static final int AB_END = AB_START + AB_STRING.length() - 1;

  /** The start position of the "ac" part of the ABAC string. */
  static final int AC_START = AB_START + AB_STRING.length() + 1;

  /** The end position of the "ac" part of the ABAC string. */
  static final int AC_END = AC_START + AC_STRING.length() - 1;

  /** Mocked action. Used to check that matches are found in the right locations. */
  @Mock Action action;

  /**
   * Mocked compiler. Used to simulate compilation of the various regular expressions, returning the
   * appropriate but handcrafted NDFA nodes instaead.
   */
  @Mock NDFACompiler compiler;

  /** A test article, the matcher implementation. */
  private Matcher m;

  /** A test article, a regexp matching an "ab" string. */
  private Regexp acRegexp;

  /** A test article, a regexp matching an "ac" string. */
  private Regexp abRegexp;

  /**
   * Instantiate test articles and set up the compiler mock to simulate proper compilation of "ab"
   * and "ac".
   */
  @BeforeEach
  public void setUp() throws RegexpParserException {
    abRegexp = new RegexpImpl(AB_STRING);
    acRegexp = new RegexpImpl(AC_STRING);

    final NDFANode abNode = CharSequenceCompiler.compile(abRegexp, AB_STRING);

    final NDFANode acNode = CharSequenceCompiler.compile(acRegexp, AC_STRING);

    when(compiler.compile(eq(abRegexp), any())).thenReturn(abNode);

    when(compiler.compile(eq(acRegexp), any())).thenReturn(acNode);

    final RegexpFactory regexpFactory =
        regexpString -> {
          if (regexpString.equals(AB_STRING)) {
            return abRegexp;
          } else if (regexpString.equals(AC_STRING)) {
            return acRegexp;
          } else {
            return RegexpFactory.DEFAULT_REGEXP_FACTORY.newRegexp(regexpString);
          }
        };

    m = new MatcherImpl(compiler, regexpFactory);
  }

  /** Test adding an action to the regexp. */
  @Test
  public final void testActionTransferToRegexpThroughRegexpStorage() throws RegexpParserException {
    assertFalse(abRegexp.hasActions(), "the regexp should not initially have actions");
    m.add(AB_STRING, action);
    assertTrue(abRegexp.hasActions(), "the regexp should have actions");
  }

  /** Test performing a match on "ab". */
  @Test
  public void testMockedMatchLength1bTerminated() throws RegexpParserException {
    final no.rmz.rmatch.utils.StringBuffer b = new no.rmz.rmatch.utils.StringBuffer(AB_STRING);
    m.add(AB_STRING, action);

    m.match(b);

    // Starting out accepting any kind of match
    verify(action).performMatch(any(Buffer.class), eq(AB_START), eq(AB_END));
  }

  /** Test performing a match on "ab". */
  @Test
  public void testMockedMatchLength1bTerminatedWithAbdc() throws RegexpParserException {
    final no.rmz.rmatch.utils.StringBuffer b = new no.rmz.rmatch.utils.StringBuffer(AC_STRING);
    m.add(AC_STRING, action);

    m.match(b);

    // Starting out accepting any kind of match
    verify(action).performMatch(any(Buffer.class), eq(AB_START), eq(AB_END));
  }

  /** Test performing a match on "ac". */
  @Test
  public void testMockedMatchLength1bTerminatedWithAbdcInLongString() throws RegexpParserException {
    final no.rmz.rmatch.utils.StringBuffer b = new no.rmz.rmatch.utils.StringBuffer(ABAC_STRING);
    m.add(AC_STRING, action);

    m.match(b);

    // Starting out accepting any kind of match
    verify(action).performMatch(any(Buffer.class), eq(AC_START), eq(AC_END));
  }

  /** Test matching the two regexps concurrently. */
  @Test
  public void testTwoPatternsStartWithTheSameLetterAndBothTriggeringMatches()
      throws RegexpParserException {
    final no.rmz.rmatch.utils.StringBuffer b = new no.rmz.rmatch.utils.StringBuffer(ABAC_STRING);

    m.add(AC_STRING, action);
    m.add(AB_STRING, action);

    m.match(b);

    GraphDumper.dump(
        "testTwoPatternsStartWithTheSameLetterAndBothTriggeringMatches", m.getNodeStorage());

    verify(action).performMatch(any(Buffer.class), eq(AB_START), eq(AB_END));
    verify(action).performMatch(any(Buffer.class), eq(AC_START), eq(AC_END));
  }
}
