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
package no.rmz.rmatch.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.impls.TestMatchers;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Table-driven specification of rmatch's basic matching semantics, with hand-computed expected
 * results. This suite is the ground truth the syntax-extension program must not disturb.
 *
 * <p>THE SEMANTIC RULE (codified 2026-07-05): for every pattern and every start position, rmatch
 * reports the LONGEST match starting at that position. Matches at different start positions are all
 * reported, including overlapping and nested ones (e.g. {@code a+} on {@code "aaa"} yields 0-2, 1-2
 * and 2-2). This differs from java.util.regex find(), which consumes input (non-overlapping); it
 * agrees with java on which SPANS are matchable from a given start.
 *
 * <p>Expected values are written as "start-end" (inclusive positions).
 */
public class BasicMatchingSemanticsTest {

  private record Case(String description, String[] patterns, String input, String... expected) {}

  private static final List<Case> CASES =
      List.of(
          // --- Literals
          new Case("single char", p("a"), "a b a", "a@0-0", "a@4-4"),
          new Case("word literal", p("cat"), "cat scatter cat", "cat@0-2", "cat@5-7", "cat@12-14"),
          new Case("overlapping occurrences", p("aa"), "aaaa", "aa@0-1", "aa@1-2", "aa@2-3"),
          new Case("no match", p("xyz"), "abc def"),
          // --- Quantifier ? (post-KB-1 semantics)
          new Case("trailing optional absent", p("ab?"), "a", "ab?@0-0"),
          new Case("trailing optional both", p("ab?"), "a ab", "ab?@0-0", "ab?@2-3"),
          new Case("middle optional", p("ab?c"), "abc ac", "ab?c@0-2", "ab?c@4-5"),
          // --- Quantifier *
          new Case("star absent", p("ab*"), "a", "ab*@0-0"),
          new Case("star loops", p("ab*"), "abbb", "ab*@0-3"),
          new Case("star longest per start", p("ba*"), "baa", "ba*@0-2"),
          // --- Quantifier +
          new Case("plus requires one", p("ab+"), "a ab abb", "ab+@2-3", "ab+@5-7"),
          new Case(
              "plus all starts",
              p("a+"),
              "aaa baa",
              "a+@0-2",
              "a+@1-2",
              "a+@2-2",
              "a+@5-6",
              "a+@6-6"),
          // --- Any char
          new Case("dot", p("a.c"), "abc axc a c", "a.c@0-2", "a.c@4-6", "a.c@8-10"),
          new Case("dot matches newline (DOTALL-always)", p("a.b"), "a\nb", "a.b@0-2"),
          new Case("dot star", p("a.*"), "abc", "a.*@0-2"),
          // --- Character classes
          new Case("simple set", p("[ab]x"), "ax bx cx", "[ab]x@0-1", "[ab]x@3-4"),
          new Case("range", p("[a-c]z"), "az bz cz dz", "[a-c]z@0-1", "[a-c]z@3-4", "[a-c]z@6-7"),
          new Case("negated set", p("[^a]x"), "ax bx xx", "[^a]x@3-4", "[^a]x@5-6", "[^a]x@6-7"),
          new Case("negated set rejects its char", p("[^a]"), "a"),
          new Case(
              "negated set does not poison other paths (KB-4)",
              p(".+[^a]?"),
              "  ab",
              ".+[^a]?@0-3",
              ".+[^a]?@1-3",
              ".+[^a]?@2-3",
              ".+[^a]?@3-3"),
          new Case(
              "set with quantifier",
              p("[ab]+"),
              "abba c",
              "[ab]+@0-3",
              "[ab]+@1-3",
              "[ab]+@2-3",
              "[ab]+@3-3"),
          // --- Alternation
          new Case("simple alternation", p("cat|dog"), "cat dog", "cat|dog@0-2", "cat|dog@4-6"),
          new Case("alternation longest wins per start", p("a|ab|abc"), "abc", "a|ab|abc@0-2"),
          new Case("shared prefix alternation", p("ab|ac"), "ab ac ad", "ab|ac@0-1", "ab|ac@3-4"),
          // --- Multiple patterns, independence
          new Case("patterns are independent", p("a", "ab", "b"), "ab", "a@0-0", "ab@0-1", "b@1-1"),
          // --- Buffer edges
          new Case("match at very start", p("ab"), "abxx", "ab@0-1"),
          new Case("match at very end", p("ab"), "xxab", "ab@2-3"),
          new Case("whole buffer", p("abc"), "abc", "abc@0-2"),
          new Case("single char buffer", p("a"), "a", "a@0-0"));

  private static String[] p(final String... patterns) {
    return patterns;
  }

  private static Set<String> matchesOf(final String[] patterns, final String input)
      throws Exception {
    final Matcher m = TestMatchers.newSingleMatcher();
    final Set<String> found = new TreeSet<>();
    for (final String pattern : patterns) {
      final String pat = pattern;
      m.add(
          pat,
          (b, start, end) -> {
            synchronized (found) {
              found.add(pat + "@" + start + "-" + end);
            }
          });
    }
    m.match(new RegexStringBuffer(input));
    m.shutdown();
    return found;
  }

  @TestFactory
  List<DynamicTest> basicSemantics() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final Case c : CASES) {
      tests.add(
          DynamicTest.dynamicTest(
              c.description(),
              () ->
                  assertEquals(
                      new TreeSet<>(List.of(c.expected())), matchesOf(c.patterns(), c.input()))));
    }
    return tests;
  }
}
