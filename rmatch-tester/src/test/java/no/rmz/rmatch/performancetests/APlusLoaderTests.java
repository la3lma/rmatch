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

import java.util.logging.Logger;
import no.rmz.rmatch.Action;
import no.rmz.rmatch.Buffer;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
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

  /** The string "a+". */
  private String aPlusString;

  /** Test item, a matcher. */
  private Matcher m;

  /** Set up test items and context. */
  @BeforeEach
  public void setUp() {
    aPlusString = "a+";
    m = MatcherFactory.newSingleMatcher();
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
}
