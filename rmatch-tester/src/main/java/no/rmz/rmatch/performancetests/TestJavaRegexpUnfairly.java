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

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;

/**
 * The intent of this class is to look for matches in the wuthering heights corpus as it appears
 * originally from the Gutenberg project. This means both looking for matches within other matches
 * and looking for overlapping matches. This will mean a lot more work for the matcher and that will
 * be unfair, which is the whole point of the exercise. This kind of matching is something the
 * rmatch matcher should be much better at.
 *
 * <p>XXXX: At present this is somewhat of an abomination. I expect that the present class will have
 * to be rewritten extensibly before it will produce results that I have any confidence in.
 */
public class TestJavaRegexpUnfairly {

  /**
   * The main method.
   *
   * @param argv Command line arguments. If present, arg 1 is interpreted as a maximum limit on the
   *     number of regexps to use.
   * @throws RegexpParserException when things go bad.
   */
  public static void main(final String[] argv) throws RegexpParserException {

    final Matcher matcher = new JavaRegexpMatcher();

    final Buffer b = new WutheringHeightsBuffer("rmatch-tester/corpus/wuthr10.txt");

    MatcherBenchmarker.testMatcher(b, matcher, argv);

    // Kill all threads and get out of here
    System.exit(0);
  }
}
