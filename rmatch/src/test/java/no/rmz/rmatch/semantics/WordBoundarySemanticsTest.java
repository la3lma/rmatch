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

/** Functional tests for ASCII word-boundary assertions: {@code \b} and {@code \B}. */
public class WordBoundarySemanticsTest {

  private record Case(String description, String pattern, String input, String... expected) {}

  private static final List<Case> CASES =
      List.of(
          new Case(
              "whole word only",
              "\\bcat\\b",
              "cat category bobcat cat!",
              "\\bcat\\b@0-2",
              "\\bcat\\b@20-22"),
          new Case(
              "word boundary before prefix",
              "\\bcat",
              "cat category bobcat cat!",
              "\\bcat@0-2",
              "\\bcat@4-6",
              "\\bcat@20-22"),
          new Case(
              "word boundary after suffix",
              "cat\\b",
              "cat category bobcat cat!",
              "cat\\b@0-2",
              "cat\\b@16-18",
              "cat\\b@20-22"),
          new Case(
              "non-word-boundary on both sides",
              "\\Bcat\\B",
              "xcaty cat catz xcat",
              "\\Bcat\\B@1-3"),
          new Case(
              "underscore and digits are word characters",
              "\\bcat\\b",
              "_cat cat_ cat2 cat",
              "\\bcat\\b@15-17"),
          new Case("boundary after newline", "\\bcat\\b", "dog\ncat\ncategory", "\\bcat\\b@4-6"));

  private static Set<String> matchesOf(final String pattern, final String input) throws Exception {
    return matchesOf(pattern, new RegexStringBuffer(input));
  }

  private static Set<String> matchesOf(final String pattern, final Buffer input) throws Exception {
    final Matcher m = new MatcherImpl(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
    final Set<String> found = new TreeSet<>();
    m.add(
        pattern,
        (b, start, end) -> {
          synchronized (found) {
            found.add(pattern + "@" + start + "-" + end);
          }
        });
    m.match(input);
    m.shutdown();
    return found;
  }

  @Test
  void wordBoundaryWorksWithCloneableNonLookaheadBuffer() throws Exception {
    assertEquals(
        new TreeSet<>(List.of("\\bcat\\b@0-2", "\\bcat\\b@8-10")),
        matchesOf("\\bcat\\b", new PlainBuffer("cat dog cat!")));
  }

  @TestFactory
  List<DynamicTest> wordBoundaries() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final Case c : CASES) {
      tests.add(
          DynamicTest.dynamicTest(
              c.description(),
              () ->
                  assertEquals(
                      new TreeSet<>(List.of(c.expected())), matchesOf(c.pattern(), c.input()))));
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
