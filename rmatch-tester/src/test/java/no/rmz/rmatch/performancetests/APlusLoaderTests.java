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
package no.rmz.rmatch.performancetests;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.logging.Logger;
import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.impls.RegexpImpl;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.performancetests.utils.StringSourceBuffer;
import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;
import no.rmz.rmatch.utils.CounterAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This is a test that checks that the regular expression "a+" can be run, and eventually also
 * compiled correctly.
 *
 * <p>This set of tests assumes that the basic mechanics works, but will stress the implementation
 * by running larg(ish) tests.
 */
@ExtendWith(MockitoExtension.class)
class APlusLoaderTests {

  /** Our Dear Logger. */
  private static final Logger LOG = Logger.getLogger(APlusLoaderTests.class.getName());

  /** If we don't get at least fire matches, something is wrong. */
  private static final int REASONABLE_MINIMUM_GUESS_OF_ASTARS_IN_WUTHERING_HEIGTHS = 10;

  /** Mocke action, used to count matches. */
  @Mock Action action;

  /** Mocked compiler used to inject precooked NDFA at appropriate spot. */
  @Mock NDFACompiler compiler;

  /** The string "a+". */
  private String aPlusString;

  /** Test item, a matcher. */
  private Matcher m;

  /** Test item, a regexp for "a+". */
  private Regexp regexp;

  /** Set up test items and context. */
  @BeforeEach
  public void setUp() throws RegexpParserException {
    final String finalAPlus = "a+";
    aPlusString = finalAPlus;
    regexp = new RegexpImpl(aPlusString);
    final NDFANode aplus = new TestCharPlusNode('a', regexp, true);

    when(compiler.compile(any(Regexp.class), any(RegexpStorage.class))).thenReturn(aplus);

    final RegexpFactory regexpFactory =
        regexpString -> {
          if (regexpString.equals(finalAPlus)) {
            return regexp;
          } else {
            return RegexpFactory.DEFAULT_REGEXP_FACTORY.newRegexp(regexpString);
          }
        };

    m = new MatcherImpl(compiler, regexpFactory);
  }

  /** Look for the pattern in a very long sequence where there will be many matches. */
  @Test
  public void testMockedVerylongMatchSequences() throws RegexpParserException {

    // Set up parameters
    final int noOfPatterns = 600;
    final String pattern = "ab";
    final int startIndexInPattern = 0;
    final int endIndexInPattern = 0;
    final int lengthOfPattern = pattern.length();

    // Build t
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < noOfPatterns; i++) {
      sb.append(pattern);
    }
    final String bufferString = sb.toString();
    final StringSourceBuffer b = new StringSourceBuffer(bufferString);
    m.add(aPlusString, action);

    m.match(b);

    for (int i = 0; i < noOfPatterns; i++) {
      final int offset = lengthOfPattern * i;
      verify(action)
          .performMatch(
              any(Buffer.class), eq(offset + startIndexInPattern), eq(offset + endIndexInPattern));
    }
  }

  /** Look for matches in the wuthering heights corpus. */
  @Test
  public void testWutheringHeightsCorpus() throws RegexpParserException {

    final Buffer b = new WutheringHeightsBuffer();
    final CounterAction counterAction = new CounterAction();

    m.add(aPlusString, counterAction);
    m.match(b);

    final int finalCount = counterAction.getCounter();
    LOG.info("Total no of 'a*' matches in Wuthering Heights is " + finalCount);
    assertTrue(finalCount > REASONABLE_MINIMUM_GUESS_OF_ASTARS_IN_WUTHERING_HEIGTHS);
  }

  /** Local test helper: node that matches one-or-more occurrences of one character. */
  private static final class TestCharPlusNode extends AbstractNDFANode {
    private final Character myChar;

    private TestCharPlusNode(final Character ch, final Regexp r, final boolean isTerminal) {
      super(r, isTerminal);
      this.myChar = ch;
    }

    @Override
    public NDFANode getNextNDFA(final Character ch) {
      return ch.equals(myChar) ? this : null;
    }

    @Override
    public Collection<PrintableEdge> getEdgesToPrint() {
      synchronized (monitor) {
        final Collection<PrintableEdge> result = getEpsilonEdgesToPrint();
        result.add(new PrintableEdge(String.valueOf(myChar), this));
        return result;
      }
    }
  }
}
