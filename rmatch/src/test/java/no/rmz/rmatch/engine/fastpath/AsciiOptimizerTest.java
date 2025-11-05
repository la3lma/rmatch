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
package no.rmz.rmatch.engine.fastpath;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for AsciiOptimizer. */
public class AsciiOptimizerTest {

  @Test
  public void testIsAscii() {
    assertTrue(AsciiOptimizer.isAscii('a'));
    assertTrue(AsciiOptimizer.isAscii('Z'));
    assertTrue(AsciiOptimizer.isAscii('0'));
    assertTrue(AsciiOptimizer.isAscii(' '));
    assertTrue(AsciiOptimizer.isAscii('\n'));
    assertTrue(AsciiOptimizer.isAscii((char) 127));

    assertFalse(AsciiOptimizer.isAscii((char) 128));
    assertFalse(AsciiOptimizer.isAscii('é'));
    assertFalse(AsciiOptimizer.isAscii('中'));
  }

  @Test
  public void testIsLetter() {
    // ASCII letters
    assertTrue(AsciiOptimizer.isLetter('a'));
    assertTrue(AsciiOptimizer.isLetter('z'));
    assertTrue(AsciiOptimizer.isLetter('A'));
    assertTrue(AsciiOptimizer.isLetter('Z'));

    // Non-letters
    assertFalse(AsciiOptimizer.isLetter('0'));
    assertFalse(AsciiOptimizer.isLetter(' '));
    assertFalse(AsciiOptimizer.isLetter('_'));

    // Unicode letters (should work via fallback)
    assertTrue(AsciiOptimizer.isLetter('é'));
    assertTrue(AsciiOptimizer.isLetter('ñ'));
  }

  @Test
  public void testIsDigit() {
    // ASCII digits
    assertTrue(AsciiOptimizer.isDigit('0'));
    assertTrue(AsciiOptimizer.isDigit('9'));

    // Non-digits
    assertFalse(AsciiOptimizer.isDigit('a'));
    assertFalse(AsciiOptimizer.isDigit(' '));
  }

  @Test
  public void testIsLetterOrDigit() {
    // ASCII letters and digits
    assertTrue(AsciiOptimizer.isLetterOrDigit('a'));
    assertTrue(AsciiOptimizer.isLetterOrDigit('Z'));
    assertTrue(AsciiOptimizer.isLetterOrDigit('0'));
    assertTrue(AsciiOptimizer.isLetterOrDigit('9'));

    // Non-alphanumeric
    assertFalse(AsciiOptimizer.isLetterOrDigit(' '));
    assertFalse(AsciiOptimizer.isLetterOrDigit('_'));
    assertFalse(AsciiOptimizer.isLetterOrDigit('!'));

    // Unicode (should work via fallback)
    assertTrue(AsciiOptimizer.isLetterOrDigit('é'));
  }

  @Test
  public void testIsWhitespace() {
    // ASCII whitespace
    assertTrue(AsciiOptimizer.isWhitespace(' '));
    assertTrue(AsciiOptimizer.isWhitespace('\t'));
    assertTrue(AsciiOptimizer.isWhitespace('\n'));
    assertTrue(AsciiOptimizer.isWhitespace('\r'));
    assertTrue(AsciiOptimizer.isWhitespace('\f'));

    // Non-whitespace
    assertFalse(AsciiOptimizer.isWhitespace('a'));
    assertFalse(AsciiOptimizer.isWhitespace('0'));
  }

  @Test
  public void testIsWord() {
    // Word characters (letters, digits, underscore)
    assertTrue(AsciiOptimizer.isWord('a'));
    assertTrue(AsciiOptimizer.isWord('Z'));
    assertTrue(AsciiOptimizer.isWord('0'));
    assertTrue(AsciiOptimizer.isWord('9'));
    assertTrue(AsciiOptimizer.isWord('_'));

    // Non-word characters
    assertFalse(AsciiOptimizer.isWord(' '));
    assertFalse(AsciiOptimizer.isWord('!'));
    assertFalse(AsciiOptimizer.isWord('-'));
  }

  @Test
  public void testFindFirstNonAscii() {
    // All ASCII
    String allAscii = "Hello World 123";
    assertEquals(allAscii.length(), AsciiOptimizer.findFirstNonAscii(allAscii, 0));

    // Unicode at start
    String unicodeStart = "éHello";
    assertEquals(0, AsciiOptimizer.findFirstNonAscii(unicodeStart, 0));

    // Unicode in middle
    String unicodeMid = "Hello é World";
    assertEquals(6, AsciiOptimizer.findFirstNonAscii(unicodeMid, 0));

    // Unicode at end
    String unicodeEnd = "Hello World é";
    assertEquals(12, AsciiOptimizer.findFirstNonAscii(unicodeEnd, 0));

    // Starting from middle
    String text = "Hello é World";
    assertEquals(6, AsciiOptimizer.findFirstNonAscii(text, 3));
    assertEquals(text.length(), AsciiOptimizer.findFirstNonAscii(text, 7));

    // Empty string
    assertEquals(0, AsciiOptimizer.findFirstNonAscii("", 0));
  }

  @Test
  public void testIsAllAscii() {
    // All ASCII
    assertTrue(AsciiOptimizer.isAllAscii("Hello World 123"));
    assertTrue(AsciiOptimizer.isAllAscii(""));
    assertTrue(AsciiOptimizer.isAllAscii("!@#$%^&*()"));

    // Contains Unicode
    assertFalse(AsciiOptimizer.isAllAscii("Hello é World"));
    assertFalse(AsciiOptimizer.isAllAscii("中文"));
    assertFalse(AsciiOptimizer.isAllAscii("Hello\u00A0World")); // Non-breaking space
  }

  @Test
  public void testConsistencyWithCharacterMethods() {
    // Test a range of characters to ensure consistency
    for (char c = 0; c < 256; c++) {
      // For ASCII range, our methods should match Character methods
      if (c < 128) {
        assertEquals(
            Character.isLetter(c),
            AsciiOptimizer.isLetter(c),
            "isLetter mismatch for char: " + (int) c);
        assertEquals(
            Character.isDigit(c),
            AsciiOptimizer.isDigit(c),
            "isDigit mismatch for char: " + (int) c);
        assertEquals(
            Character.isLetterOrDigit(c),
            AsciiOptimizer.isLetterOrDigit(c),
            "isLetterOrDigit mismatch for char: " + (int) c);
        assertEquals(
            Character.isWhitespace(c),
            AsciiOptimizer.isWhitespace(c),
            "isWhitespace mismatch for char: " + (int) c);
      }
    }
  }

  @Test
  public void testFindFirstNonAsciiWithMultipleChunks() {
    // Test the 4-character-at-a-time optimization
    // Position 17 is after 4 full chunks (4*4 = 16) plus one more character
    final int positionAfterMultipleChunks = 17;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < positionAfterMultipleChunks; i++) {
      sb.append('a');
    }
    sb.append('é'); // Unicode at position 17

    assertEquals(positionAfterMultipleChunks, AsciiOptimizer.findFirstNonAscii(sb.toString(), 0));

    // Test with Unicode at positions aligned with chunk boundaries
    String test4 = "aaaé"; // Position 3
    assertEquals(3, AsciiOptimizer.findFirstNonAscii(test4, 0));

    String test5 = "aaaaé"; // Position 4 (at chunk boundary)
    assertEquals(4, AsciiOptimizer.findFirstNonAscii(test5, 0));

    String test8 = "aaaaaaaaé"; // Position 8
    assertEquals(8, AsciiOptimizer.findFirstNonAscii(test8, 0));
  }
}
