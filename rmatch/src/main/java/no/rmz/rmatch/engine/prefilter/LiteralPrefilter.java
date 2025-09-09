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

  /**
   * Result wrapper for literal extraction.
   *
   * @param hint The extracted literal hint, if any.
   */
  public record Result(
      @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<LiteralHint> hint) {
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
   * <p>Uses aggressive heuristics to find the most selective literal: 1. Prefers anchored prefixes
   * (most selective) 2. Prefers longer literals (more selective than shorter ones) 3. Prefers
   * literals with rare characters (more selective than common ones) 4. Requires at least 2
   * characters to be considered useful
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

    // Find the best literal using selectivity heuristics
    String bestLiteral = "";
    double bestScore = 0.0;
    int bestStart = -1;

    for (final String literal : literals) {
      if (literal.length() < 2) {
        continue; // Skip too-short literals
      }

      final int start = regex.indexOf(literal);
      final boolean anchored = regex.startsWith("^") && start <= 1;

      // Calculate selectivity score
      double score = calculateSelectivityScore(literal, anchored);

      if (score > bestScore) {
        bestScore = score;
        bestLiteral = literal;
        bestStart = start;
      }
    }

    // Require at least length >= 2 to be useful
    if (bestLiteral.length() < 2) {
      return Optional.empty();
    }

    final boolean anchored = regex.startsWith("^") && bestStart <= 1;
    final boolean ci = ((flags & java.util.regex.Pattern.CASE_INSENSITIVE) != 0);
    final int literalOffsetInMatch = anchored ? bestStart : 0;

    return Optional.of(
        new LiteralHint(patternId, bestLiteral, bestStart, anchored, ci, literalOffsetInMatch));
  }

  /**
   * Calculates a selectivity score for a literal substring. Higher scores indicate more selective
   * (less common) literals.
   *
   * @param literal the literal substring
   * @param anchored whether the literal is anchored at the start
   * @return selectivity score (higher = more selective)
   */
  private static double calculateSelectivityScore(final String literal, final boolean anchored) {
    // Base score from length (longer is more selective)
    double score = literal.length() * 10.0;

    // Bonus for anchored literals (much more selective)
    if (anchored) {
      score *= 3.0;
    }

    // Bonus for rare characters (case-sensitive scoring)
    for (int i = 0; i < literal.length(); i++) {
      final char c = literal.charAt(i);

      // Common vowels and consonants are less selective
      if ("aeiouAEIOU".indexOf(c) >= 0) {
        score += 1.0; // vowels are common
      } else if ("thnrsldcmpfgywbvkjxqzTHNRSLDCMPFGYWBVKJXQZ".indexOf(c) >= 0) {
        score += 2.0; // consonants vary in frequency
      } else if (Character.isDigit(c)) {
        score += 5.0; // digits are fairly selective
      } else if ("!@#$%^&*()_+-=[]{}|;':,.<>?/~`".indexOf(c) >= 0) {
        score += 10.0; // punctuation is very selective
      } else {
        score += 3.0; // other characters
      }
    }

    // Penalty for very common short words in English text
    final String lowerLiteral = literal.toLowerCase();
    if ("the and for are but not you all can had has was were said from they have with this that what there when where who will more time very first well way may down day much use than more come some could out many write would like into over think also back after their just where those only new know take year good work three never before end through last right old see too any same another much while should still such make need life little world public hand big group system small number part way even place case work week government company right way good first time life work way back right over take state without much work need may think know never still much"
        .contains(lowerLiteral)) {
      score *= 0.5; // penalty for very common English words
    }

    return score;
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
          if (!current.isEmpty()) {
            literals.add(current.toString());
            current.setLength(0);
          }
          inQuote = false;
          i++; // skip the 'E'
        } else {
          // Inside quoted section, everything is literal
          current.append(c);
        }
        continue;
      }

      if (escaped) {
        // Handle escaped characters
        if (c == 'Q') {
          // Start of quoted section
          if (!current.isEmpty()) {
            literals.add(current.toString());
            current.setLength(0);
          }
          inQuote = true;
        } else if ("nrtfaebBAdDsSwWzZ".indexOf(c) >= 0) {
          // Special escape sequences end current literal
          if (!current.isEmpty()) {
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
        if (!current.isEmpty()) {
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
        if (!current.isEmpty()) {
          literals.add(current.toString());
          current.setLength(0);
        }
        i += 2; // skip ?:
        continue;
      }

      switch (c) {
        case '[':
          inClass = true;
          if (!current.isEmpty()) {
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
          if (!current.isEmpty()) {
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
    if (!current.isEmpty()) {
      literals.add(current.toString());
    }

    return literals;
  }

  /** Private constructor to prevent instantiation. */
  private LiteralPrefilter() {
    // Utility class
  }
}
