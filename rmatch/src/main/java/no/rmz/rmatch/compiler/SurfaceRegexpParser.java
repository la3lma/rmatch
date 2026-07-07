/*
 Copyright 2012. Bjørn Remseth (rmz@rmz.no).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package no.rmz.rmatch.compiler;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

/**
 * Parser for the supported rmatch regular-expression surface syntax.
 *
 * <p>The parser does not build automata directly. Instead, it reports parsed constructs to an
 * {@link AbstractRegexBuilder}, which decides how to represent them.
 */
public final class SurfaceRegexpParser {

  /** Commit mode used when buffered literal text should be emitted only if non-empty. */
  private static final boolean COMMIT_ONLY_IF_SOMETHING_IN_SB = true;

  /** Commit mode used when an empty literal fragment is meaningful. */
  private static final boolean COMMIT_EMPTY_STRING_IF_NOTHING_IN_SB = false;

  /** Recipient of parsed regular-expression events. */
  private final AbstractRegexBuilder arb;

  /**
   * Create a parser that reports parsed constructs to the supplied builder.
   *
   * @param arb builder that receives parse events
   */
  public SurfaceRegexpParser(final AbstractRegexBuilder arb) {
    this.arb = checkNotNull(arb);
  }

  /**
   * Parse a regular-expression string.
   *
   * @throws RegexpParserException if the string is malformed or uses unsupported syntax
   */
  public void parse(final String regexString) throws RegexpParserException {
    new PAux(regexString, arb).parse();
  }

  /** A helper class used to parse regular expressions. */
  static final class PAux {

    /** A compiler backend. */
    private final AbstractRegexBuilder arb;

    /** A StringBuilder used to parse string segments in the input regexp. */
    private StringBuilder sb;

    /** A source of characters based on the input string. */
    private final StringSource src;

    /** The raw regexp source, kept for counted-quantifier atom replay. */
    private final String regexString;

    /** Current group nesting depth; must be zero at end of input. */
    private int groupDepth = 0;

    /** Source span [start, end) of the last completed atom, or -1 when no atom is available. */
    private int lastAtomStart = -1;

    /** See lastAtomStart. */
    private int lastAtomEnd = -1;

    /** Source start positions of currently open groups (for atom-span tracking). */
    private final java.util.ArrayDeque<Integer> openGroupStarts = new java.util.ArrayDeque<>();

    /** Maximum total expansion count for counted quantifiers. */
    private static final int MAX_COUNTED_REPETITION = 1000;

    /** True when a "(?i)" prefix requested case-insensitive matching. */
    private boolean caseInsensitive = false;

    /**
     * Create a new helper class instance.
     *
     * @param regexString the string to parse.
     * @param arb the builder to use.
     */
    PAux(final String regexString, final AbstractRegexBuilder arb) {
      this(regexString, arb, false);
    }

    PAux(final String regexString, final AbstractRegexBuilder arb, final boolean caseInsensitive) {
      this.arb = checkNotNull(arb);
      this.sb = new StringBuilder();
      this.regexString = regexString;
      this.src = new StringSource(regexString);
      this.caseInsensitive = caseInsensitive;
    }

    /**
     * send the current string to the compiler backend, then start a new StringBuilder.
     *
     * @param ifNotEmpty XXX Don't understand this.
     */
    private void commitCurrentString(final boolean ifNotEmpty) {
      final String str = sb.toString();
      if (ifNotEmpty) {
        if (str.isEmpty()) {
          return;
        }
      }
      emitString(str);
      sb = new StringBuilder();
    }

    /** Emit a literal string, applying case folding when requested. */
    private void emitString(final String str) {
      if (!caseInsensitive) {
        arb.addString(str);
        return;
      }
      // Case-insensitive: foldable characters become two-case character sets; unfoldable runs
      // stay plain strings.
      final StringBuilder plain = new StringBuilder();
      for (int i = 0; i < str.length(); i++) {
        final char c = str.charAt(i);
        final char lo = Character.toLowerCase(c);
        final char up = Character.toUpperCase(c);
        if (lo != up) {
          if (!plain.isEmpty()) {
            arb.addString(plain.toString());
            plain.setLength(0);
          }
          arb.startCharSet();
          arb.addToCharSet(String.valueOf(lo) + up);
          arb.endCharSet();
        } else {
          plain.append(c);
        }
      }
      if (!plain.isEmpty()) {
        arb.addString(plain.toString());
      }
    }

    /**
     * Commit the accumulated literal so that a quantifier that follows binds to the LAST ATOM only,
     * per standard regex semantics. "ab?" must mean a(b?), not (ab)?.
     *
     * <p>The accumulated string (if any) is split: everything before the last character is
     * committed as one fragment, then the last character is committed as its own single-atom
     * fragment for the quantifier to attach to. When the string is empty the preceding construct
     * (character set, any-char, ...) has already been committed as its own fragment and is the
     * correct quantifier target as-is.
     *
     * <p>This was KB-1: quantifiers bound to the whole preceding literal, so "ab?" compiled as
     * (ab)? — matching the empty string and "ab" but not "a", and making the FOLLOWING atom
     * spuriously matchable on its own in patterns like "ab?c".
     */
    private void commitForQuantifier() {
      final int len = sb.length();
      if (len == 0) {
        return;
      }
      if (len > 1) {
        emitString(sb.substring(0, len - 1));
      }
      emitString(sb.substring(len - 1));
      sb = new StringBuilder();
    }

    /**
     * The objective is to parse all legal regexps as described in
     * http://en.wikipedia.org/wiki/Regular_expression That's an interesting goal in itself, however
     * it may in fact be better to emulate java's regexp syntax.
     *
     * <p>That's the lofty objectives, the reality is much more humble. We can parse this expression
     * "abc[ab][^de]z?f+x*|y" and expressions containing the same constructs (character sequences,
     * character sets (and inverted sets), various optional subexpressions of single-char length,
     * and not much more. This will eventually change, but for now that's what we've got.
     *
     * @throws RegexpParserException when bad things happen during parsing.
     */
    void parse() throws RegexpParserException {
      consumeFlagPrefix();
      while (src.hasNext()) {
        final char ch = src.next();
        parseNextChar(ch);
      }
      if (groupDepth != 0) {
        throw new RegexpParserException("Unbalanced group: missing ')'");
      }
      commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
    }

    // XXX Missing {m,n}, meaning "match at least m,
    //     but no more than n times modifier.

    private void parseNextChar(char ch) throws RegexpParserException {
      final int atomStart = src.getIndex() - 1;
      switch (ch) {
        case '|':
          commitCurrentString(COMMIT_EMPTY_STRING_IF_NOTHING_IN_SB);
          arb.separateAlternatives();
          lastAtomStart = -1;
          break;
        case '\\':
          parseQuotedChar();
          lastAtomStart = atomStart;
          lastAtomEnd = src.getIndex();
          break;
        case '.':
          commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
          arb.addAnyChar();
          lastAtomStart = atomStart;
          lastAtomEnd = src.getIndex();
          break;
        case '^':
          commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
          arb.addBeginningOfLine();
          break;
        case '$':
          commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
          arb.addEndOfLine();
          break;
        case '?':
          commitForQuantifier();
          arb.addOptionalSingular();
          lastAtomStart = -1; // a quantified atom cannot take another counted quantifier
          break;
        case '*':
          commitForQuantifier();
          arb.addOptionalZeroOrMulti();
          lastAtomStart = -1;
          break;
        case '+':
          commitForQuantifier();
          arb.addOptionalOnceOrMulti();
          lastAtomStart = -1;
          break;
        case '{':
          parseCountedQuantifier();
          lastAtomStart = -1;
          break;
        case '[':
          commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
          parseCharSet();
          lastAtomStart = atomStart;
          lastAtomEnd = src.getIndex();
          break;
        case '(':
          commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
          parseGroupStart();
          openGroupStarts.push(atomStart);
          break;
        case ')':
          if (groupDepth == 0) {
            throw new RegexpParserException("')' without matching '('");
          }
          commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
          arb.endGroup();
          groupDepth--;
          lastAtomStart = openGroupStarts.pop();
          lastAtomEnd = src.getIndex();
          break;
        default:
          sb.append(ch);
          lastAtomStart = atomStart;
          lastAtomEnd = src.getIndex();
          break;
      }
    }

    /**
     * Consume a leading "(?i)", "(?s)", "(?is)" or "(?si)" flag prefix, if present. "i" enables
     * case-insensitive compilation (case folding at the character/class level); "s" (DOTALL) is
     * accepted as a documented no-op because '.' already matches every character including newline.
     * Flags are prefix-only: pattern identity in RegexpStorage is the raw string, so the flag
     * travels with the pattern. A "(?i)" later in the pattern is a parse error (via the
     * unsupported-group-construct path).
     */
    private void consumeFlagPrefix() {
      final java.util.regex.Matcher m =
          java.util.regex.Pattern.compile("^\\(\\?([is]+)\\)").matcher(regexString);
      if (m.find()) {
        for (int i = 0; i < m.end(); i++) {
          src.next();
        }
        if (m.group(1).indexOf('i') >= 0) {
          caseInsensitive = true;
        }
      }
    }

    /**
     * Parse "{m}", "{m,n}" or "{m,}" and apply it to the last atom by replay: the atom's source
     * text is re-parsed (m-1) more times, then (n-m) optional copies (or one starred copy for
     * open-ended). Semantics: X{2,4} == XXX?X?.
     */
    private void parseCountedQuantifier() throws RegexpParserException {
      if (lastAtomStart < 0) {
        throw new RegexpParserException("Counted quantifier '{' with no preceding atom");
      }
      final String atomText = regexString.substring(lastAtomStart, lastAtomEnd);

      final int m = parseCountNumber(true);
      final int n; // -1 means open-ended
      final Character sep = src.peek();
      if (sep == null) {
        throw new RegexpParserException("Unterminated counted quantifier");
      }
      if (sep == '}') {
        src.next();
        n = m;
      } else if (sep == ',') {
        src.next();
        final Character after = src.peek();
        if (after != null && after == '}') {
          src.next();
          n = -1;
        } else {
          n = parseCountNumber(false);
          if (src.peek() == null || src.next() != '}') {
            throw new RegexpParserException("Expected '}' terminating counted quantifier");
          }
        }
      } else {
        throw new RegexpParserException("Malformed counted quantifier");
      }

      if (n >= 0 && n < m) {
        throw new RegexpParserException("Counted quantifier {" + m + "," + n + "} has max < min");
      }
      if (m == 0 && n == 0) {
        throw new RegexpParserException("Counted quantifier {0} is not supported");
      }
      final int total = n >= 0 ? n : m;
      if (total > MAX_COUNTED_REPETITION || m > MAX_COUNTED_REPETITION) {
        throw new RegexpParserException(
            "Counted quantifier exceeds maximum repetition " + MAX_COUNTED_REPETITION);
      }

      // The atom has already been emitted once. Make sure it stands alone as the last fragment
      // (split it out of any accumulated literal), exactly as for ? * +.
      commitForQuantifier();

      if (m == 0) {
        // First (already emitted) copy becomes optional.
        arb.addOptionalSingular();
      }
      // Additional REQUIRED copies: copies 2..m.
      for (int i = 1; i < m; i++) {
        replayAtom(atomText);
      }
      if (n < 0) {
        // Open-ended: one more copy, starred.
        replayAtom(atomText);
        arb.addOptionalZeroOrMulti();
      } else {
        // Bounded: (n - max(m,1)... ) optional copies. Copies beyond the required ones.
        final int required = Math.max(m, 1); // copy 1 exists even when m == 0 (made optional)
        for (int i = required; i < n; i++) {
          replayAtom(atomText);
          arb.addOptionalSingular();
        }
      }
    }

    /** Re-parse the atom's source text so it is emitted again as the last fragment. */
    private void replayAtom(final String atomText) throws RegexpParserException {
      final PAux sub = new PAux(atomText, arb, caseInsensitive);
      // Replay within the same builder scope: groups inside atomText are balanced by
      // construction, so this cannot unbalance the enclosing scopes.
      sub.parse();
    }

    /** Parse a decimal number inside a counted quantifier. */
    private int parseCountNumber(final boolean atStart) throws RegexpParserException {
      final StringBuilder num = new StringBuilder();
      while (src.peek() != null && Character.isDigit(src.peek())) {
        num.append(src.next());
      }
      if (num.isEmpty()) {
        throw new RegexpParserException(
            atStart
                ? "Counted quantifier '{' must be followed by a number"
                : "Expected number after ',' in counted quantifier");
      }
      try {
        return Integer.parseInt(num.toString());
      } catch (NumberFormatException e) {
        throw new RegexpParserException("Number too large in counted quantifier");
      }
    }

    private void parseGroupStart() throws RegexpParserException {
      // "(?" introduces special group constructs; only the non-capturing "(?:" is supported
      // (capturing does not exist in rmatch, so "(...)" and "(?:...)" are equivalent).
      final Character nxt = src.peek();
      if (nxt != null && nxt == '?') {
        src.next();
        final Character nxt2 = src.peek();
        if (nxt2 != null && nxt2 == ':') {
          src.next();
        } else {
          throw new RegexpParserException(
              "Unsupported group construct '(?" + (nxt2 == null ? "" : nxt2) + "'");
        }
      }
      arb.startGroup();
      groupDepth++;
    }

    private void parseQuotedChar() throws RegexpParserException {
      // KB-2: this condition was inverted, making EVERY escape throw.
      if (!src.hasNext()) {
        throw new RegexpParserException("Expected char after escape char: \\");
      }
      final char ch = src.next();
      switch (ch) {
        // Literal escapes: the metacharacter itself.
        case '\\', '.', '*', '+', '?', '[', ']', '(', ')', '|', '^', '$', '-', '{', '}':
          sb.append(ch);
          break;
        // Control escapes.
        case 'n':
          sb.append('\n');
          break;
        case 't':
          sb.append('\t');
          break;
        case 'r':
          sb.append('\r');
          break;
        case 'f':
          sb.append('\f');
          break;
        // Shorthand character classes: sugar over the existing charset machinery.
        case 'd', 'D', 'w', 'W', 's', 'S':
          commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
          emitShorthandClass(ch);
          break;
        default:
          throw new RegexpParserException("Unsupported escape '\\" + ch + "'");
      }
    }

    /** Emit \d \D \w \W \s \S as a (possibly inverted) character set fragment. */
    private void emitShorthandClass(final char c) {
      arb.startCharSet();
      if (Character.isUpperCase(c)) {
        arb.invertCharSet();
      }
      addShorthandMembers(Character.toLowerCase(c));
      arb.endCharSet();
    }

    /** Add the member characters of a lowercase shorthand class to the open charset. */
    private void addShorthandMembers(final char c) {
      switch (c) {
        case 'd' -> arb.addRangeToCharSet('0', '9');
        case 'w' -> {
          arb.addRangeToCharSet('a', 'z');
          arb.addRangeToCharSet('A', 'Z');
          arb.addRangeToCharSet('0', '9');
          arb.addToCharSet("_");
        }
        case 's' -> arb.addToCharSet(" \t\n\u000B\f\r");
        default -> throw new IllegalStateException("not a shorthand class: " + c);
      }
    }

    private void parseCharSet() throws RegexpParserException {
      char ch;
      arb.startCharSet();
      final Character nxt = src.peek();

      if (nxt == null) {
        throw new RegexpParserException("Unterminated char set, missing ']'");
      }

      if (nxt == '^') {
        arb.invertCharSet();
        src.next();
      }

      boolean parsingRange = false;
      while (src.hasNext()) {
        ch = src.next();
        if (ch == ']') {
          break;
        } else if (ch == '\\') {
          if (!src.hasNext()) {
            throw new RegexpParserException("Expected char after escape char in charset");
          }
          final char esc = src.next();
          switch (esc) {
            case '\\', ']', '[', '-', '^' -> sb.append(esc);
            case 'n' -> sb.append('\n');
            case 't' -> sb.append('\t');
            case 'r' -> sb.append('\r');
            case 'f' -> sb.append('\f');
            case 'd', 'w', 's' -> {
              // Flush accumulated plain chars first, then add the class members.
              final String pending = sb.toString();
              if (!pending.isEmpty()) {
                addCharsToSet(pending);
                sb = new StringBuilder();
              }
              addShorthandMembers(esc);
            }
            default ->
                throw new RegexpParserException(
                    "Unsupported escape '\\" + esc + "' inside character set");
          }
        } else if (ch == '-') {
          parsingRange = true;
        } else if (parsingRange) {
          final String s = sb.toString();
          final int l = sb.length();
          if (l > 1) {
            addCharsToSet(s.substring(0, l - 1));
          }
          addRangeToSet(s.charAt(l - 1), ch);
          sb = new StringBuilder();
          parsingRange = false;
        } else {
          sb.append(ch);
        }
      }

      final String cs = sb.toString();
      if (!cs.isEmpty()) {
        addCharsToSet(cs);
        sb = new StringBuilder();
      }

      arb.endCharSet();
    }

    /** Add chars to the open charset, adding case-folded variants when folding. */
    private void addCharsToSet(final String cs) {
      if (!caseInsensitive) {
        arb.addToCharSet(cs);
        return;
      }
      final StringBuilder folded = new StringBuilder(cs);
      for (int i = 0; i < cs.length(); i++) {
        final char c = cs.charAt(i);
        final char lo = Character.toLowerCase(c);
        final char up = Character.toUpperCase(c);
        if (lo != up) {
          folded.append(lo).append(up);
        }
      }
      arb.addToCharSet(folded.toString());
    }

    /** Add a range to the open charset; under folding also add the case-swapped range. */
    private void addRangeToSet(final char from, final char to) {
      arb.addRangeToCharSet(from, to);
      if (caseInsensitive) {
        final char fromLo = Character.toLowerCase(from);
        final char toLo = Character.toLowerCase(to);
        final char fromUp = Character.toUpperCase(from);
        final char toUp = Character.toUpperCase(to);
        if (fromLo != fromUp && toLo != toUp) {
          if (from != fromLo || to != toLo) {
            arb.addRangeToCharSet(fromLo, toLo);
          }
          if (from != fromUp || to != toUp) {
            arb.addRangeToCharSet(fromUp, toUp);
          }
        }
      }
    }
  }
}
