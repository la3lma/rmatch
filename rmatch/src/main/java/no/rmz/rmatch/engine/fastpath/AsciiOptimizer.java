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

/**
 * ASCII fast-path optimization for common character operations.
 *
 * <p>This class provides optimized lookup tables and methods for ASCII characters (0-127), allowing
 * branch-free character classification that is faster than Character.* methods. For non-ASCII
 * characters, falls back to standard methods.
 *
 * <p>Enables micro-architecture-aware optimization by using:
 *
 * <ul>
 *   <li>Compact lookup tables that fit in CPU cache
 *   <li>Branch-free table lookups instead of conditional logic
 *   <li>Simple array indexing instead of method calls
 * </ul>
 */
public final class AsciiOptimizer {

  /** Bit flag for letter classification. */
  private static final byte LETTER = 1;

  /** Bit flag for digit classification. */
  private static final byte DIGIT = 2;

  /** Bit flag for whitespace classification. */
  private static final byte WHITESPACE = 4;

  /** Bit flag for word character (letter, digit, or underscore). */
  private static final byte WORD = 8;

  /**
   * Lookup table for ASCII character properties. Index by character code (0-127), get bit flags for
   * that character's properties. Uses byte for compact representation (fits in L1 cache).
   */
  private static final byte[] ASCII_PROPERTIES = new byte[128];

  static {
    // Initialize the lookup table
    for (int i = 0; i < 128; i++) {
      byte flags = 0;
      char c = (char) i;

      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        flags |= LETTER | WORD;
      }
      if (c >= '0' && c <= '9') {
        flags |= DIGIT | WORD;
      }
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
        flags |= WHITESPACE;
      }
      if (c == '_') {
        flags |= WORD;
      }

      ASCII_PROPERTIES[i] = flags;
    }
  }

  /** Private constructor to prevent instantiation. */
  private AsciiOptimizer() {}

  /**
   * Fast check if a character is ASCII (0-127).
   *
   * @param c the character to check
   * @return true if the character is ASCII
   */
  public static boolean isAscii(final char c) {
    return c < 128;
  }

  /**
   * Fast check if a character is an ASCII letter. For non-ASCII, falls back to
   * Character.isLetter().
   *
   * @param c the character to check
   * @return true if the character is a letter
   */
  public static boolean isLetter(final char c) {
    if (c < 128) {
      return (ASCII_PROPERTIES[c] & LETTER) != 0;
    }
    return Character.isLetter(c);
  }

  /**
   * Fast check if a character is an ASCII digit. For non-ASCII, falls back to Character.isDigit().
   *
   * @param c the character to check
   * @return true if the character is a digit
   */
  public static boolean isDigit(final char c) {
    if (c < 128) {
      return (ASCII_PROPERTIES[c] & DIGIT) != 0;
    }
    return Character.isDigit(c);
  }

  /**
   * Fast check if a character is an ASCII letter or digit. For non-ASCII, falls back to
   * Character.isLetterOrDigit().
   *
   * @param c the character to check
   * @return true if the character is a letter or digit
   */
  public static boolean isLetterOrDigit(final char c) {
    if (c < 128) {
      return (ASCII_PROPERTIES[c] & (LETTER | DIGIT)) != 0;
    }
    return Character.isLetterOrDigit(c);
  }

  /**
   * Fast check if a character is an ASCII whitespace character. For non-ASCII, falls back to
   * Character.isWhitespace().
   *
   * @param c the character to check
   * @return true if the character is whitespace
   */
  public static boolean isWhitespace(final char c) {
    if (c < 128) {
      return (ASCII_PROPERTIES[c] & WHITESPACE) != 0;
    }
    return Character.isWhitespace(c);
  }

  /**
   * Fast check if a character is a word character (letter, digit, or underscore). This is commonly
   * used in regex \w class.
   *
   * @param c the character to check
   * @return true if the character is a word character
   */
  public static boolean isWord(final char c) {
    if (c < 128) {
      return (ASCII_PROPERTIES[c] & WORD) != 0;
    }
    // For non-ASCII, check if it's a letter or digit
    return Character.isLetterOrDigit(c);
  }

  /**
   * Scan a character array or CharSequence to find the longest ASCII span starting at a given
   * position.
   *
   * <p>This is useful for switching between ASCII fast-path and Unicode handling.
   *
   * @param text the text to scan
   * @param start the starting position
   * @return the index of the first non-ASCII character, or text length if all ASCII
   */
  public static int findFirstNonAscii(final CharSequence text, final int start) {
    final int length = text.length();
    int i = start;

    // Process 4 characters at a time for better throughput
    while (i + 3 < length) {
      final char c0 = text.charAt(i);
      final char c1 = text.charAt(i + 1);
      final char c2 = text.charAt(i + 2);
      final char c3 = text.charAt(i + 3);

      if ((c0 | c1 | c2 | c3) >= 128) {
        // At least one non-ASCII character, find which one
        if (c0 >= 128) return i;
        if (c1 >= 128) return i + 1;
        if (c2 >= 128) return i + 2;
        return i + 3;
      }
      i += 4;
    }

    // Process remaining characters one at a time
    while (i < length) {
      if (text.charAt(i) >= 128) {
        return i;
      }
      i++;
    }

    return i;
  }

  /**
   * Check if an entire character sequence is ASCII.
   *
   * @param text the text to check
   * @return true if all characters are ASCII
   */
  public static boolean isAllAscii(final CharSequence text) {
    return findFirstNonAscii(text, 0) == text.length();
  }
}
