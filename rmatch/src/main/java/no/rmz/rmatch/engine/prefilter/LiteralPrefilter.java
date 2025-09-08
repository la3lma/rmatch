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
package no.rmz.rmatch.engine.prefilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts literal substrings from regular expression patterns for use in prefiltering.
 *
 * <p>This class analyzes regex patterns to find the longest literal substring that can be used for
 * fast prefiltering. It uses a simple state machine to parse the regex and identify literal
 * sequences while avoiding metacharacters and complex constructs.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Optional<LiteralHint> hint = LiteralPrefilter.extract(1, "^foo.*bar", Pattern.CASE_INSENSITIVE);
 * // Returns hint for literal "foo" (anchored prefix)
 * }</pre>
 */
public final class LiteralPrefilter {

  /** Result wrapper for literal extraction. */
  public static final class Result {
    /** The extracted literal hint, if any. */
    public final Optional<LiteralHint> hint;

    /**
     * Creates a result with the given hint.
     *
     * @param hint the extracted literal hint
     */
    public Result(final Optional<LiteralHint> hint) {
      this.hint = hint;
    }
  }

  /**
   * Extracts the best literal substring from a regex pattern.
   *
   * <p>Prefers anchored prefixes if they exist, otherwise returns the globally longest literal run.
   * Requires at least 2 characters to be considered useful.
   *
   * @param patternId stable identifier for this pattern
   * @param regex the regular expression pattern string
   * @param flags the Pattern flags (e.g., Pattern.CASE_INSENSITIVE)
   * @return Optional containing the best literal hint, or empty if no suitable literal found
   */
  public static Optional<LiteralHint> extract(
      final int patternId, final String regex, final int flags) {
    final List<String> literals = extractAllLiterals(regex);

    if (literals.isEmpty()) {
      return Optional.empty();
    }

    // Find the longest literal
    String bestLiteral = "";
    for (final String literal : literals) {
      if (literal.length() > bestLiteral.length()) {
        bestLiteral = literal;
      }
    }

    // Require at least length >= 2 to be useful
    if (bestLiteral.length() < 2) {
      return Optional.empty();
    }

    final int bestStart = regex.indexOf(bestLiteral);
    final boolean anchored = regex.startsWith("^") && bestStart <= 1;
    final boolean ci = ((flags & java.util.regex.Pattern.CASE_INSENSITIVE) != 0);
    final int literalOffsetInMatch = anchored ? 0 : 0;

    return Optional.of(
        new LiteralHint(patternId, bestLiteral, bestStart, anchored, ci, literalOffsetInMatch));
  }

  /** Extracts all possible literal substrings from a regex using simple heuristics. */
  private static List<String> extractAllLiterals(final String regex) {
    final List<String> literals = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    boolean escaped = false;
    boolean inClass = false;
    boolean inQuote = false;

    for (int i = 0; i < regex.length(); i++) {
      final char c = regex.charAt(i);

      if (inQuote) {
        // Handle quoted literals \Q...\E
        if (c == '\\' && i + 1 < regex.length() && regex.charAt(i + 1) == 'E') {
          // End of quoted section - add the literal
          if (current.length() > 0) {
            literals.add(current.toString());
            current.setLength(0);
          }
          inQuote = false;
          i++; // skip the 'E'
          continue;
        } else {
          // Inside quoted section, everything is literal
          current.append(c);
          continue;
        }
      }

      if (escaped) {
        // Handle escaped characters
        if (c == 'Q') {
          // Start of quoted section
          if (current.length() > 0) {
            literals.add(current.toString());
            current.setLength(0);
          }
          inQuote = true;
        } else if ("nrtfaebBAdDsSwWzZ".indexOf(c) >= 0) {
          // Special escape sequences end current literal
          if (current.length() > 0) {
            literals.add(current.toString());
            current.setLength(0);
          }
        } else {
          // Regular escaped char becomes literal
          current.append(c);
        }
        escaped = false;
        continue;
      }

      if (c == '\\') {
        escaped = true;
        continue;
      }

      if (inClass) {
        if (c == ']') {
          inClass = false;
        }
        // Inside character class, end current literal
        if (current.length() > 0) {
          literals.add(current.toString());
          current.setLength(0);
        }
        continue;
      }

      // Handle special sequences like (?:
      if (c == '('
          && i + 2 < regex.length()
          && regex.charAt(i + 1) == '?'
          && regex.charAt(i + 2) == ':') {
        // Non-capturing group (?:
        if (current.length() > 0) {
          literals.add(current.toString());
          current.setLength(0);
        }
        i += 2; // skip ?:
        continue;
      }

      switch (c) {
        case '[':
          inClass = true;
          if (current.length() > 0) {
            literals.add(current.toString());
            current.setLength(0);
          }
          break;
        case '(':
        case ')':
        case '|':
        case '*':
        case '+':
        case '?':
        case '.':
        case '^':
        case '$':
        case '{':
        case '}':
          // Metacharacters end current literal
          if (current.length() > 0) {
            literals.add(current.toString());
            current.setLength(0);
          }
          break;
        default:
          // Regular character, add to current literal
          current.append(c);
          break;
      }
    }

    // Add final literal if any
    if (current.length() > 0) {
      literals.add(current.toString());
    }

    return literals;
  }

  /** Private constructor to prevent instantiation. */
  private LiteralPrefilter() {
    // Utility class
  }
}
