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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import no.rmz.rmatch.Action;
import no.rmz.rmatch.Buffer;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.RMatch;
import no.rmz.rmatch.RegexpParserException;
import no.rmz.rmatch.impls.TestMatchers;
import no.rmz.rmatch.interfaces.Regexp;
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
    m = TestMatchers.newSingleMatcher();
  }

  /** Test matching the two regexps concurrently. */
  @Test
  public final void testUseOfOrdinaryMatcherImpl() throws RegexpParserException {
    final Buffer b = RMatch.stringBuffer(("ab" + " " + "ac"));

    m.add("ac", action);
    m.add("ab", action);

    m.match(b);

    verify(action).performMatch(any(Buffer.class), eq(0L), eq((long) "ab".length()));
    verify(action)
        .performMatch(
            any(Buffer.class), eq("ab".length() + 1L), eq("ab".length() + 1L + "ac".length()));
  }

  /** Test the public facade shown in the README copy-paste example. */
  @Test
  public final void testPublicFacadeCreatesMatcherAndStringBuffer() throws Exception {
    final AtomicReference<String> matched = new AtomicReference<>();
    final Matcher matcher = RMatch.newSingleMatcher();
    matcher.add("WARN", (buffer, start, end) -> matched.set(buffer.getString(start, end)));

    matcher.match(RMatch.stringBuffer("INFO user:alice WARN disk nearly full"));
    matcher.close();

    assertEquals("WARN", matched.get());
  }

  /** Test the explicit materializing string-buffer overloads. */
  @Test
  public final void testStringBufferConvenienceMethodsMaterializeInput() throws Exception {
    assertBufferContainsExactly(RMatch.stringBuffer("alpha"), "alpha");
    assertBufferContainsExactly(RMatch.stringBuffer(new StringBuilder("bravo")), "bravo");

    final Path path = Files.createTempFile("rmatch-string-buffer-", ".txt");
    try {
      Files.writeString(path, "charlie", StandardCharsets.UTF_8);
      assertBufferContainsExactly(RMatch.stringBuffer(path, StandardCharsets.UTF_8), "charlie");
    } finally {
      Files.deleteIfExists(path);
    }

    assertBufferContainsExactly(RMatch.stringBuffer(new StringReader("delta")), "delta");
    assertBufferContainsExactly(
        RMatch.stringBuffer(
            new ByteArrayInputStream("echo".getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8),
        "echo");
  }

  private static void assertBufferContainsExactly(final Buffer buffer, final String expected) {
    assertEquals(expected, buffer.getString(0, expected.length()));
  }
}
