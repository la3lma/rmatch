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
package no.rmz.rmatch.ordinaryuse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.testutils.GraphDumper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This is a basic test of two or more NDFA nodes after one another encoding a sequence. It runs
 * with handcrafted nodes (no compilers), and tests the basic matcher algorithm. If this doesn't
 * work then nothing will.
 */
@ExtendWith(MockitoExtension.class)
public class OrdinaryUsageSmokeTest {

  /** Mocked action. Used to check that matches are found in the right locations. */
  @Mock Action action;

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
  public void setUp() {
    m = new MatcherImpl();
  }

  /** Test matching the two regexps concurrently. */
  @Test
  public final void testUseOfOrdinaryMatcherImpl() throws RegexpParserException {
    final no.rmz.rmatch.utils.StringBuffer b =
        new no.rmz.rmatch.utils.StringBuffer(("ab" + " " + "ac"));

    m.add("ac", action);
    m.add("ab", action);

    m.match(b);

    GraphDumper.dump("fnord", m.getNodeStorage());

    verify(action).performMatch(any(Buffer.class), eq(0), eq("ab".length() - 1));
    verify(action)
        .performMatch(
            any(Buffer.class), eq("ab".length() + 1), eq("ab".length() + 1 + "ac".length() - 1));
  }
}
