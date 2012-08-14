/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.compiler;

import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.impls.RegexpImpl;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.interfaces.RegexpStorage;
import no.rmz.rmatch.testutils.GraphDumper;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Test the compiler for abstract regular expressions.
 */
@RunWith(MockitoJUnitRunner.class)
public class ARegexpCompilerTest {

    /**
     * The compiler will be mocked up to produce results of running
     * the test article.
     */
    @Mock
    NDFACompiler compiler;

    /**
     * The action is an op.
     */
    @Mock
    Action action;

    /**
     * Used inject content to the test article in the tests.
     */
    private interface InjectorOfAbstractRegexp {

        /**
         * Inject a bunch of abstract regexp elements into
         * arb for it toc compile.
         * @param arb A compiler for abstract regexps.
         */
        void inject(final AbstractRegexBuilder arb);
    }

    /**
     * A helper class that holds a matcher and a buffer, and
     * is consequently, and unimaginatively, called "MB".  Sorry folks,
     * but that' the truth.
     */
    public static final class MB {

        /**
         * The matcher.
         */
        private final Matcher m;

        /**
         * The buffer.
         */
        private final Buffer b;

        /**
         * Making the pair.
         * @param m matcher.
         * @param b buffer.
         */
        public MB(final Matcher m, final Buffer b) {
            this.m = m;
            this.b = b;
        }

        /**
         * Get the B.
         * @return  the B.
         */
        public Buffer getB() {
            return b;
        }

        /**
         * get the M.
         * @return the M.
         */
        public Matcher getM() {
            return m;
        }
    }

    /**
     * Inject some content to the test article (a compiler instance),
     * then mock up the NDFACompiler instance to return that
     * compilation result when asked to compile the regexpPattern.
     *
     * Then run a match against the testString and return the Matcher
     * Buffer pair so that the tests that use this method can set up
     * its own mockito verify statements that are test specific.
     *
     * @param regexpPattern The regular expression to compile.
     * @param testString a string to match
     * @param injector Injector of content to the ARegexpCompiler.
     * @return an MB pair representing the state of the matcher.
     */
    private MB runMatcherFromCompiler(
            final String regexpPattern,
            final String testString,
            final InjectorOfAbstractRegexp injector) throws RegexpParserException {
        final Regexp regexp = new RegexpImpl(regexpPattern);
        final ARegexpCompiler arc = new ARegexpCompiler(regexp);
        injector.inject(arc);
        final NDFANode abcNode = arc.getResult();

        when(compiler.compile((Regexp) any(), // XXX SHould have used "eq" here.
                (RegexpStorage) any())).thenReturn(abcNode);

        final RegexpFactory regexpFactory = new RegexpFactory() {
            @Override
            public Regexp newRegexp(final String regexpString) {
                if (regexpString.equals(regexpPattern)) {
                    return regexp;
                } else {
                    return RegexpFactory.DEFAULT_REGEXP_FACTORY
                            .newRegexp(regexpString);
                }
            }
        };

        final Matcher m = new MatcherImpl(compiler, regexpFactory);
        m.add(regexpPattern, action);

        final   no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(testString);

        m.match(b);
        return new MB(m, b);

    }

    /**
     * Test of addString method, of class ARegexpCompiler.
     */
    @Test
    public final  void testAddString() throws RegexpParserException {

        final String regexpPattern = "a";
        final String testString = "a";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addString(regexpPattern);
                    }
                });

        GraphDumper.dump("ARegexpCompilerTestAddString",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test of addString method, of class ARegexpCompiler.
     */
    @Test
    public final  void testAddStringLength2() throws RegexpParserException {

        final String regexpPattern = "ab";
        final String testString = "abcd";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addString("a");
                        arb.addString("b");
                    }
                });

        GraphDumper.dump("testAddStringLength2",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 1);
    }

    /**
     * Test of separateAlternatives method, of class ARegexpCompiler.
     */
    @Test
    public final void testSeparateAlternatives() throws RegexpParserException {
        final String regexpPattern = "a|b";
        final String testString = "b";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addString("a");
                        arb.separateAlternatives();
                        arb.addString("b");
                    }
                });

        GraphDumper.dump("ARegexpCompilerTestSeparateAlternatives",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test recognition of a char set.
     */
    @Test
    public final  void testCharSet() throws RegexpParserException {
        final String regexpPattern = "[ab]";
        final String testString = "b";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.startCharSet();
                        arb.addToCharSet("a");
                        arb.addToCharSet("b");
                        arb.endCharSet();
                    }
                });

        GraphDumper.dump("testCharSet",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test recognition of a char set.
     */
    @Test
    public final void testInvertedCharSet() throws RegexpParserException {
        final String regexpPattern = "[^ab]";
        final String testString = "c";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.startCharSet();
                        arb.invertCharSet();
                        arb.addToCharSet("a");
                        arb.addToCharSet("b");
                        arb.endCharSet();
                    }
                });

        GraphDumper.dump("testInverseCharSet",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test of addRangeToCharSet method, of class ARegexpCompiler.
     */
    @Test
    public final void testAddRangeToCharSet() throws RegexpParserException {
        final String regexpPattern = "[a-c]";
        final String testString = "b";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.startCharSet();
                        arb.addRangeToCharSet('a', 'c');
                        arb.endCharSet();
                    }
                });

        GraphDumper.dump("testAddRangeToCharSet",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test of addAnyChar method, of class ARegexpCompiler.
     */
    @Test
    public final void testAddAnyChar() throws RegexpParserException {
        final String regexpPattern = ".";
        final String testString = "z";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addAnyChar();
                    }
                });

        GraphDumper.dump("testAddAnyChar",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test of addBeginningOfLine method, of class ARegexpCompiler.
     */
    @Ignore
    @Test
    public final void testAddBeginningOfLine() throws RegexpParserException {
        final String regexpPattern = "^z";
        final String testString = "z";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addBeginningOfLine();
                        arb.addString("z");
                        arb.addAnyChar();
                    }
                });

        GraphDumper.dump("testAddBeginningOfLine",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test of addEndOfLine method, of class ARegexpCompiler.
     */
    @Ignore
    @Test
    public final void testAddEndOfLine() throws RegexpParserException {
        final String regexpPattern = "z$";
        final String testString = "z";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addBeginningOfLine();
                        arb.addString("z");
                        arb.addAnyChar();
                    }
                });

        GraphDumper.dump("testAddendOfLine",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test of addOptionalSingular method, of class ARegexpCompiler.
     */
    @Test
    public final void testAddOptionalSingular() throws RegexpParserException {
        final String regexpPattern = "a?b";
        final String testString = "b";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addString("a");
                        arb.addOptionalSingular();
                        arb.addString("b");
                    }
                });

        GraphDumper.dump("testAddOptionalSingular",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 0);
    }

    /**
     * Test of addOptionalZeroOrMulti method, of class ARegexpCompiler.
     */
    @Test
    public final void testAddOptionalZeroOrMulti() throws RegexpParserException {
        final String regexpPattern = "ba*n";
        final String testString = "baaaan";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addString("b");
                        arb.addString("a");
                        arb.addOptionalZeroOrMulti();
                        arb.addString("n");
                    }
                });

        GraphDumper.dump("testAddOptionalZeroOrMulti",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 5);
    }

    /**
     * Test of addOptionalOnceOrMulti method, of class ARegexpCompiler.
     */
    @Test
    public final void testAddOptionalOnceOrMulti() throws RegexpParserException {

        final String regexpPattern = "ba+n";
        final String testString = "baaaan";
        final MB mb = runMatcherFromCompiler(
                regexpPattern,
                testString,
                new InjectorOfAbstractRegexp() {
                    @Override
                    public void inject(final AbstractRegexBuilder arb) {
                        arb.addString("b");
                        arb.addString("a");
                        arb.addOptionalOnceOrMulti();
                        arb.addString("n");
                    }
                });

        GraphDumper.dump("testAddOptionalOnceOrMulti",
                mb.getM().getNodeStorage());
        verify(action).performMatch(mb.getB(), 0, 5);
    }
}
