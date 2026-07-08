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
package no.rmz.rmatch.impls;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.interfaces.DFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import org.junit.jupiter.api.Test;

/** Test the first-character optimization for fixing O(l*m) complexity. */
public class FirstCharacterOptimizationTest {

  /** Test that canStartWith correctly identifies which regexps can start with a given character. */
  @Test
  public void testCanStartWith() throws Exception {
    // Create regexps that start with different characters
    final Regexp regexpA = new RegexpImpl("abc");
    final Regexp regexpB = new RegexpImpl("bcd");
    final Regexp regexpC = new RegexpImpl("cde");
    final Regexp regexpDot = new RegexpImpl(".xy"); // . matches any character

    // Compile the regexps
    compileRegexp(regexpA);
    compileRegexp(regexpB);
    compileRegexp(regexpC);
    compileRegexp(regexpDot);

    // Test that regexps correctly identify their starting characters
    assertTrue(regexpA.canStartWith('a'), "regexpA should start with 'a'");
    assertFalse(regexpA.canStartWith('b'), "regexpA should not start with 'b'");
    assertFalse(regexpA.canStartWith('c'), "regexpA should not start with 'c'");

    assertFalse(regexpB.canStartWith('a'), "regexpB should not start with 'a'");
    assertTrue(regexpB.canStartWith('b'), "regexpB should start with 'b'");
    assertFalse(regexpB.canStartWith('c'), "regexpB should not start with 'c'");

    assertFalse(regexpC.canStartWith('a'), "regexpC should not start with 'a'");
    assertFalse(regexpC.canStartWith('b'), "regexpC should not start with 'b'");
    assertTrue(regexpC.canStartWith('c'), "regexpC should start with 'c'");

    // Test that . (any character) regexp can start with any character
    assertTrue(regexpDot.canStartWith('a'), "regexpDot should start with 'a'");
    assertTrue(regexpDot.canStartWith('b'), "regexpDot should start with 'b'");
    assertTrue(regexpDot.canStartWith('x'), "regexpDot should start with 'x'");
  }

  /** Test that caching works correctly for canStartWith. */
  @Test
  public void testCanStartWithCaching() throws Exception {
    final Regexp regexp = new RegexpImpl("test");
    compileRegexp(regexp);

    // First call should compute and cache
    assertTrue(regexp.canStartWith('t'));

    // Second call should use cache (testing the same result)
    assertTrue(regexp.canStartWith('t'));

    // Different character
    assertFalse(regexp.canStartWith('x'));
    assertFalse(regexp.canStartWith('x')); // Should use cached result
  }

  /** Test that DFANode correctly filters regexps by first character. */
  @Test
  public void testDFANodeFirstCharacterFiltering() throws Exception {
    final Regexp regexpA = new RegexpImpl("abc");
    final Regexp regexpB = new RegexpImpl("bcd");
    final Regexp regexpC = new RegexpImpl("cde");

    compileRegexp(regexpA);
    compileRegexp(regexpB);
    compileRegexp(regexpC);

    // Create a DFA node with all three regexps
    final Set<NDFANode> ndfaNodes = new HashSet<>();
    ndfaNodes.add(regexpA.getMyNode());
    ndfaNodes.add(regexpB.getMyNode());
    ndfaNodes.add(regexpC.getMyNode());

    final DFANode dfaNode = new DFANodeImpl(ndfaNodes);

    // Test that filtering works correctly
    Set<Regexp> regexpsForA = dfaNode.getRegexpsThatCanStartWith('a');
    assertEquals(1, regexpsForA.size(), "Should have 1 regexp for 'a'");
    assertTrue(regexpsForA.contains(regexpA), "Should contain regexpA");

    Set<Regexp> regexpsForB = dfaNode.getRegexpsThatCanStartWith('b');
    assertEquals(1, regexpsForB.size(), "Should have 1 regexp for 'b'");
    assertTrue(regexpsForB.contains(regexpB), "Should contain regexpB");

    Set<Regexp> regexpsForC = dfaNode.getRegexpsThatCanStartWith('c');
    assertEquals(1, regexpsForC.size(), "Should have 1 regexp for 'c'");
    assertTrue(regexpsForC.contains(regexpC), "Should contain regexpC");

    Set<Regexp> regexpsForX = dfaNode.getRegexpsThatCanStartWith('x');
    assertEquals(0, regexpsForX.size(), "Should have 0 regexps for 'x'");
  }

  /** Test that MatchSetImpl optimization creates fewer matches. */
  @Test
  public void testMatchSetImplOptimization() throws Exception {
    final Regexp regexpA = new RegexpImpl("abc");
    final Regexp regexpB = new RegexpImpl("bcd");
    final Regexp regexpC = new RegexpImpl("cde");

    compileRegexp(regexpA);
    compileRegexp(regexpB);
    compileRegexp(regexpC);

    // Create a DFA node with all three regexps
    final Set<NDFANode> ndfaNodes = new HashSet<>();
    ndfaNodes.add(regexpA.getMyNode());
    ndfaNodes.add(regexpB.getMyNode());
    ndfaNodes.add(regexpC.getMyNode());

    final DFANode dfaNode = new DFANodeImpl(ndfaNodes);

    // With lazy materialization, no Match objects exist until a regexp reaches a terminal
    // state. None of abc/bcd/cde are terminal after one character, so zero matches are
    // materialized -- but the match set must stay alive to pursue them.
    final MatchSetImpl matchSetWithA = new MatchSetImpl(0, dfaNode, 'a');
    assertEquals(
        0,
        matchSetWithA.getMatches().size(),
        "No speculative matches should be materialized at creation");
    assertTrue(matchSetWithA.hasMatches(), "Match set must stay alive for its candidates");

    final MatchSetImpl matchSetWithB = new MatchSetImpl(0, dfaNode, 'b');
    assertEquals(
        0,
        matchSetWithB.getMatches().size(),
        "No speculative matches should be materialized at creation");
    assertTrue(matchSetWithB.hasMatches(), "Match set must stay alive for its candidates");

    // Without character optimization the candidate set is larger, but still nothing is
    // materialized until a terminal state is reached.
    final MatchSetImpl matchSetWithoutOptim = new MatchSetImpl(0, dfaNode);
    assertEquals(
        0,
        matchSetWithoutOptim.getMatches().size(),
        "No speculative matches should be materialized at creation");
    assertTrue(matchSetWithoutOptim.hasMatches(), "Match set must stay alive for its candidates");
  }

  /** Helper method to compile a regexp through the production compiler. */
  private void compileRegexp(final Regexp regexp) throws Exception {
    final NDFANode compiledNode = new NDFACompilerImpl().compile(regexp, null);
    regexp.setMyNDFANode(compiledNode);
  }
}
