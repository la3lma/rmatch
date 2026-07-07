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
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Functional tests for the first anchor sub-issue: {@code ^} and {@code $}. */
public class LineAnchorSemanticsTest {

  private record Case(String description, String[] patterns, String input, String... expected) {}

  private static final List<Case> CASES =
      List.of(
          new Case("start of buffer", p("^a"), "a ba", "^a@0-0"),
          new Case("start after newline", p("^a"), "x\na ba\nabc", "^a@2-2", "^a@7-7"),
          new Case("not after ordinary whitespace", p("^a"), "x a"),
          new Case("end at eof", p("a$"), "ba", "a$@1-1"),
          new Case("end before newline", p("a$"), "ba\nca ", "a$@1-1"),
          new Case("whole line", p("^ab$"), "ab\nxab\nabx\nab", "^ab$@0-1", "^ab$@11-12"),
          new Case(
              "anchored and unanchored patterns mix",
              p("^ab", "ab"),
              "zab\nab",
              "ab@1-2",
              "^ab@4-5",
              "ab@4-5"),
          new Case(
              "line-end inside alternation is semantic",
              p("foo$|foobar"),
              "foobar foo\nfoo",
              "foo$|foobar@0-5",
              "foo$|foobar@7-9",
              "foo$|foobar@11-13"),
          new Case(
              "line-start inside alternation is semantic",
              p("^foo|bar"),
              "xfoo\nbar foo",
              "^foo|bar@5-7"),
          new Case(
              "anchors inside groups",
              p("(^ab|cd$)"),
              "ab\nxcd\ncd",
              "(^ab|cd$)@0-1",
              "(^ab|cd$)@4-5",
              "(^ab|cd$)@7-8"));

  private static String[] p(final String... patterns) {
    return patterns;
  }

  private static Set<String> matchesOf(final String[] patterns, final String input)
      throws Exception {
    return matchesOf(patterns, new RegexStringBuffer(input));
  }

  private static Set<String> matchesOf(final String[] patterns, final Buffer input)
      throws Exception {
    final Matcher m = new MatcherImpl(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
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
    m.match(input);
    m.shutdown();
    return found;
  }

  @Test
  void endAnchorWorksBeforeNewlineForCloneableNonLookaheadBuffer() throws Exception {
    assertEquals(new TreeSet<>(List.of("a$@1-1")), matchesOf(p("a$"), new PlainBuffer("ba\nca ")));
  }

  @TestFactory
  List<DynamicTest> lineAnchors() {
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

  private static final class PlainBuffer implements Buffer {
    private final String text;
    private int currentPos = -1;

    private PlainBuffer(final String text) {
      this.text = text;
    }

    private PlainBuffer(final PlainBuffer other) {
      this.text = other.text;
      this.currentPos = other.currentPos;
    }

    @Override
    public String getCurrentRestString() {
      return text.substring(Math.min(currentPos + 1, text.length()));
    }

    @Override
    public String getString(final int start, final int stop) {
      return text.substring(start, stop);
    }

    @Override
    public boolean hasNext() {
      return currentPos < text.length() - 1;
    }

    @Override
    public Character getNext() {
      currentPos += 1;
      return text.charAt(currentPos);
    }

    @Override
    public int getCurrentPos() {
      return currentPos;
    }

    @Override
    public Buffer clone() {
      return new PlainBuffer(this);
    }
  }
}
