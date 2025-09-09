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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.impls.RegexpImpl;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.testutils.GraphDumper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Test the compiler for abstract regular expressions. */
@ExtendWith(MockitoExtension.class)
public class ARegexpCompilerTest {

  /** The compiler will be mocked up to produce results of running the test article. */
  @Mock NDFACompiler compiler;

  /** The action is an op. */
  @Mock Action action;

  /** Used inject content to the test article in the tests. */
  private interface InjectorOfAbstractRegexp {

    /**
     * Inject a bunch of abstract regexp elements into arb for it toc compile.
     *
     * @param arb A compiler for abstract regexps.
     */
    void inject(final AbstractRegexBuilder arb);
  }

  /**
   * A helper class that holds a matcher and a buffer, and is consequently, and unimaginatively,
   * called "MB". Sorry folks, but that' the truth.
   *
   * @param m The matcher.
   * @param b The buffer.
   */
  public record MB(Matcher m, Buffer b) {

    /**
     * Making the pair.
     *
     * @param m matcher.
     * @param b buffer.
     */
    public MB {}

    /**
     * Get the B.
     *
     * @return the B.
     */
    @Override
    public Buffer b() {
      return b;
    }

    /**
     * get the M.
     *
     * @return the M.
     */
    @Override
    public Matcher m() {
      return m;
    }
  }

  /**
   * Inject some content to the test article (a compiler instance), then mock up the NDFACompiler
   * instance to return that compilation result when asked to compile the regexpPattern.
   *
   * <p>Then run a match against the testString and return the Matcher Buffer pair so that the tests
   * that use this method can set up its own mockito verify statements that are test specific.
   *
   * @param regexpPattern The regular expression to compile.
   * @param testString a string to match
   * @param injector Injector of content to the ARegexpCompiler.
   * @return an MB pair representing the state of the matcher.
   */
  private MB runMatcherFromCompiler(
      final String regexpPattern, final String testString, final InjectorOfAbstractRegexp injector)
      throws RegexpParserException {
    final Regexp regexp = new RegexpImpl(regexpPattern);
    final ARegexpCompiler arc = new ARegexpCompiler(regexp);
    injector.inject(arc);
    final NDFANode abcNode = arc.getResult();

    when(compiler.compile(
            any(), // XXX SHould have used "eq" here.
            any()))
        .thenReturn(abcNode);

    final RegexpFactory regexpFactory =
        regexpString -> {
          if (regexpString.equals(regexpPattern)) {
            return regexp;
          } else {
            return RegexpFactory.DEFAULT_REGEXP_FACTORY.newRegexp(regexpString);
          }
        };

    final Matcher m = new MatcherImpl(compiler, regexpFactory);
    m.add(regexpPattern, action);

    final Buffer b = new no.rmz.rmatch.utils.StringBuffer(testString);

    m.match(b);
    return new MB(m, b);
  }

  /**
   * Verify match
   *
   * @param mb The MB instance
   * @param nameOfTest name of test
   * @param stop end of match
   */
  private void verifyPerformMatch(final MB mb, final String nameOfTest, final int stop) {
    GraphDumper.dump(nameOfTest, mb.m().getNodeStorage());
    verify(action).performMatch(any(Buffer.class), eq(0), eq(stop));
  }

  /** Test of addString method, of class ARegexpCompiler. */
  @Test
  public final void testAddString() throws RegexpParserException {

    final String regexpPattern = "a";
    final String testString = "a";
    final MB mb =
        runMatcherFromCompiler(regexpPattern, testString, arb -> arb.addString(regexpPattern));

    verifyPerformMatch(mb, "ARegexpCompilerTestAddString", 0);
  }

  /** Test of addString method, of class ARegexpCompiler. */
  @Test
  public final void testAddStringLength2() throws RegexpParserException {

    final String regexpPattern = "ab";
    final String testString = "abcd";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.addString("a");
              arb.addString("b");
            });

    verifyPerformMatch(mb, "testAddStringLength2", 1);
  }

  /** Test of separateAlternatives method, of class ARegexpCompiler. */
  @Test
  public final void testSeparateAlternatives() throws RegexpParserException {
    final String regexpPattern = "a|b";
    final String testString = "b";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.addString("a");
              arb.separateAlternatives();
              arb.addString("b");
            });

    verifyPerformMatch(mb, "ARegexpCompilerTestSeparateAlternatives", 0);
  }

  /** Test recognition of a char set. */
  @Test
  public final void testCharSet() throws RegexpParserException {
    final String regexpPattern = "[ab]";
    final String testString = "b";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.startCharSet();
              arb.addToCharSet("a");
              arb.addToCharSet("b");
              arb.endCharSet();
            });

    verifyPerformMatch(mb, "testCharSet", 0);
  }

  /** Test recognition of a char set. */
  @Test
  public final void testInvertedCharSet() throws RegexpParserException {
    final String regexpPattern = "[^ab]";
    final String testString = "c";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.startCharSet();
              arb.invertCharSet();
              arb.addToCharSet("a");
              arb.addToCharSet("b");
              arb.endCharSet();
            });

    verifyPerformMatch(mb, "testInverseCharSet", 0);
  }

  /** Test of addRangeToCharSet method, of class ARegexpCompiler. */
  @Test
  public final void testAddRangeToCharSet() throws RegexpParserException {
    final String regexpPattern = "[a-c]";
    final String testString = "b";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.startCharSet();
              arb.addRangeToCharSet('a', 'c');
              arb.endCharSet();
            });

    verifyPerformMatch(mb, "testAddRangeToCharSet", 0);
  }

  /** Test of addAnyChar method, of class ARegexpCompiler. */
  @Test
  public final void testAddAnyChar() throws RegexpParserException {
    final String regexpPattern = ".";
    final String testString = "z";
    final MB mb =
        runMatcherFromCompiler(regexpPattern, testString, AbstractRegexBuilder::addAnyChar);

    verifyPerformMatch(mb, "testAddAnyChar", 0);
  }

  /** Test of addBeginningOfLine method, of class ARegexpCompiler. */
  @Disabled
  @Test
  public final void testAddBeginningOfLine() throws RegexpParserException {
    final String regexpPattern = "^z";
    final String testString = "z";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.addBeginningOfLine();
              arb.addString("z");
              arb.addAnyChar();
            });

    verifyPerformMatch(mb, "testAddBeginningOfLine", 0);
  }

  /** Test of addEndOfLine method, of class ARegexpCompiler. */
  @Disabled
  @Test
  public final void testAddEndOfLine() throws RegexpParserException {
    final String regexpPattern = "z$";
    final String testString = "z";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.addBeginningOfLine();
              arb.addString("z");
              arb.addAnyChar();
            });
    verifyPerformMatch(mb, "testAddendOfLine", 0);
  }

  /** Test of addOptionalSingular method, of class ARegexpCompiler. */
  @Test
  public final void testAddOptionalSingular() throws RegexpParserException {
    final String regexpPattern = "a?b";
    final String testString = "b";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.addString("a");
              arb.addOptionalSingular();
              arb.addString("b");
            });
    verifyPerformMatch(mb, "testAddOptionalSingular", 0);
  }

  /** Test of addOptionalZeroOrMulti method, of class ARegexpCompiler. */
  @Test
  public final void testAddOptionalZeroOrMulti() throws RegexpParserException {
    final String regexpPattern = "ba*n";
    final String testString = "baaaan";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.addString("b");
              arb.addString("a");
              arb.addOptionalZeroOrMulti();
              arb.addString("n");
            });

    verifyPerformMatch(mb, "testAddOptionalZeroOrMulti", 5);
  }

  /** Test of addOptionalOnceOrMulti method, of class ARegexpCompiler. */
  @Test
  public final void testAddOptionalOnceOrMulti() throws RegexpParserException {

    final String regexpPattern = "ba+n";
    final String testString = "baaaan";
    final MB mb =
        runMatcherFromCompiler(
            regexpPattern,
            testString,
            arb -> {
              arb.addString("b");
              arb.addString("a");
              arb.addOptionalOnceOrMulti();
              arb.addString("n");
            });

    verifyPerformMatch(mb, "testAddOptionalOnceOrMulti", 5);
  }
}
