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
package no.rmz.rmatch;

import java.util.Set;

/**
 * Per-pattern option flags for {@link Matcher#add(String, Set, Action)}.
 *
 * <p>This enum fixes the shape of the flags API for the 2.0 line: future matching modes join as new
 * constants, which is a binary-compatible change, so the {@code Matcher} interface never needs
 * another overload for them. Flags express the same semantics as the corresponding inline pattern
 * syntax; they exist so that programmatically assembled pattern sets do not have to splice mode
 * prefixes into pattern strings.
 */
public enum PatternFlag {

  /**
   * Match without regard to letter case, equivalent to prefixing the pattern with {@code (?i)}.
   * Case folding happens at compile time; the scan path is unchanged.
   */
  CASE_INSENSITIVE("(?i)");

  /** Inline-syntax prefix expressing this flag. */
  private final String inlinePrefix;

  PatternFlag(final String inlinePrefix) {
    this.inlinePrefix = inlinePrefix;
  }

  /**
   * Rewrite a pattern so the supplied flags are expressed in inline syntax.
   *
   * <p>Flags are prepended in declaration order, so the rewritten pattern is deterministic for a
   * given flag set. An empty or {@code null} flag set returns the pattern unchanged.
   *
   * @param regex pattern text in the rmatch supported syntax subset
   * @param flags flags to apply; {@code null} is treated as empty
   * @return pattern text with the flags expressed as inline prefixes
   */
  static String applyTo(final String regex, final Set<PatternFlag> flags) {
    if (flags == null || flags.isEmpty()) {
      return regex;
    }
    final StringBuilder sb = new StringBuilder();
    for (final PatternFlag flag : values()) {
      if (flags.contains(flag)) {
        sb.append(flag.inlinePrefix);
      }
    }
    return sb.append(regex).toString();
  }
}
