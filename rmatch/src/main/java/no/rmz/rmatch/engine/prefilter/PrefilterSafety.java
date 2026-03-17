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
package no.rmz.rmatch.engine.prefilter;

/**
 * Conservative safety checks for prefilter hints.
 *
 * <p>These guards prefer correctness over aggressive filtering. A hint is considered safe only when
 * it is a prefix-aligned literal for the regex, and the regex does not contain alternation.
 */
public final class PrefilterSafety {
  /**
   * Determine if a literal hint is safe to use for start-position filtering.
   *
   * @param regex source regex
   * @param hint extracted literal hint
   * @return true if the hint can be safely used for candidate start filtering
   */
  public static boolean isSafeHint(final String regex, final LiteralHint hint) {
    if (regex == null || hint == null) {
      return false;
    }
    // Alternation means no single literal is guaranteed to appear in every match path.
    if (regex.indexOf('|') >= 0) {
      return false;
    }
    // We only trust literal prefixes with a stable zero offset in matched text.
    final int expectedPrefixOffset = regex.startsWith("^") ? 1 : 0;
    return hint.offsetInRegex() == expectedPrefixOffset && hint.literalOffsetInMatch() == 0;
  }

  private PrefilterSafety() {}
}
