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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.RMatch;
import no.rmz.rmatch.RegexpParserException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Regression tests for malformed patterns that previously escaped the parser contract. */
public class MalformedPatternTest {

  @TestFactory
  List<DynamicTest> unterminatedCharacterClassesAreRejected() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final String pattern : List.of("[", "[^", "[abc", "[^abc", "[a-z")) {
      tests.add(
          DynamicTest.dynamicTest(
              pattern,
              () -> {
                try (Matcher matcher = RMatch.newSingleMatcher()) {
                  final RegexpParserException error =
                      assertThrows(
                          RegexpParserException.class, () -> matcher.add(pattern, (b, s, e) -> {}));
                  assertTrue(error.getMessage().contains("character class"), error::getMessage);
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> quantifiersWithoutPrecedingAtomsAreRejected() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final String pattern : List.of("?a", "*a", "+a", "a|?b", "^*a", "a$+", "\\b?word")) {
      tests.add(
          DynamicTest.dynamicTest(
              pattern,
              () -> {
                try (Matcher matcher = RMatch.newSingleMatcher()) {
                  final RegexpParserException error =
                      assertThrows(
                          RegexpParserException.class, () -> matcher.add(pattern, (b, s, e) -> {}));
                  assertTrue(error.getMessage().contains("no preceding atom"), error::getMessage);
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> matcherRemainsUsableAfterMalformedPattern() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final String pattern : List.of("[abc", "?a", "*a", "+a")) {
      tests.add(
          DynamicTest.dynamicTest(
              pattern,
              () -> {
                try (Matcher matcher = RMatch.newSingleMatcher()) {
                  assertThrows(
                      RegexpParserException.class, () -> matcher.add(pattern, (b, s, e) -> {}));
                  assertDoesNotThrow(
                      () -> {
                        matcher.add("valid", (b, s, e) -> {});
                        matcher.match(RMatch.stringBuffer("valid"));
                      });
                }
              }));
    }
    return tests;
  }
}
