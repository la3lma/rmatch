# Regex syntax and semantics

This document defines the regular-expression language and match-reporting
behavior proposed for rmatch 2.0. rmatch is not a drop-in replacement for
`java.util.regex`, PCRE, or RE2. A pattern outside this documented surface is
unsupported even if a particular build happens to accept it.

## Supported syntax

| Construct | Syntax | Meaning |
|---|---|---|
| Literal | `abc` | The exact character sequence |
| Concatenation | `ab` | Match `a`, then `b` |
| Alternation | `a\|b` | Match either branch |
| Optional | `a?` | Zero or one `a` |
| Repetition | `a*`, `a+` | Zero-or-more or one-or-more |
| Counted repetition | `a{m}`, `a{m,n}`, `a{m,}` | Exact, bounded, or lower-bounded repetition |
| Grouping | `(ab)`, `(?:ab)` | Grouping without capture semantics |
| Any character | `.` | Any character, including newline |
| Character class | `[abc]`, `[a-z]` | One listed character or range member |
| Negated class | `[^abc]` | One character outside the class |
| Shorthand class | `\d`, `\w`, `\s` | ASCII digit, word, or whitespace character |
| Negated shorthand | `\D`, `\W`, `\S` | Complement of the corresponding shorthand |
| Line anchors | `^`, `$` | Start or end of a line as defined below |
| Word assertions | `\b`, `\B` | ASCII word boundary or non-boundary |
| Prefix flag | `(?i)` | ASCII case-insensitive matching for the pattern |
| DOTALL prefix | `(?s)` | Accepted; `.` already matches newline |

`(?i)` and `(?s)` may be combined at the beginning, for example `(?is)a.b`.
Flags are prefix-only. Applications may express case-insensitivity without
rewriting pattern strings by passing `PatternFlag.CASE_INSENSITIVE` to
`Matcher.add`.

Counted repetition expands the compiled expression and is capped at 1,000.
Invalid or excessive bounds fail during pattern registration.

## Escapes and character rules

The supported escaped literals include `\\`, `\.`, `\*`, `\+`, `\?`, `\[`,
and `\(`. The control escapes `\n`, `\t`, `\r`, and `\f` are supported.
Unknown escapes and a trailing backslash are errors.

The shorthand and boundary definitions are intentionally ASCII-oriented:

- `\d` is `[0-9]`.
- `\w` is ASCII letters, digits, and underscore.
- `\s` is space, tab, newline, vertical tab, form feed, or carriage return.
- `\b` is a position where exactly one adjacent character is a `\w`
  character; `\B` is its complement.

Case-insensitive matching applies Java's single-`char` upper- and lower-case
mapping at compile time. It does not promise locale-sensitive or multi-code-
point Unicode folding, and 2.0 makes no Unicode property-class contract.

## Anchors

`^` and `$` are line anchors without requiring a separate multiline mode:

- `^` succeeds at position zero and immediately after `\n`.
- `$` succeeds at end of input and immediately before `\n`.

Anchors are semantic assertions and may appear inside groups and alternations.
Input-only anchors such as `\A`, `\z`, and `\Z` are not supported.

## Match selection

For each registered pattern and each input start position, rmatch reports the
longest match beginning at that position. Matches beginning at different
positions are all eligible, including overlapping and nested spans. For
example, `a+` against `aaa` reports `[0,3)`, `[1,3)`, and `[2,3)`.

Different registered patterns are independent. If both `a` and `ab` are
registered, each may report its own match beginning at the same position.
Registering distinct actions for one pattern keeps the actions distinct.

Callbacks receive half-open `[start, end)` offsets, matching
`String.substring(start, end)` and `Buffer.getString(start, end)`. Callback
ordering is unspecified. Partitioned matchers may call actions concurrently.

Pure zero-width patterns are not part of the 2.0 reporting contract. Assertions
are supported when they constrain a match that consumes at least one
character.

## Deliberate exclusions

The following are not supported in 2.0:

- capture groups and captured-substring extraction
- backreferences such as `\1`
- lookahead and lookbehind
- scoped or mid-pattern flags such as `a(?i)b`
- lazy and possessive quantifiers
- atomic groups
- Unicode property classes
- input-only anchors `\A`, `\z`, and `\Z`
- pure zero-width match reporting
- a non-DOTALL mode for `.`

Backreferences are non-regular and conflict with rmatch's finite-automaton
model. Other exclusions may be investigated later, but they are not implicit
2.x compatibility promises.

## Errors

`Matcher.add` throws `RegexpParserException` when a pattern is malformed or
uses unsupported syntax. The matcher remains available for registering a valid
pattern after such a failure. Applications generating large pattern sets
should report the rejected pattern together with the exception message.

## Stability boundary

For the 2.x line, semantic-versioning compatibility covers the exported
`no.rmz.rmatch` API and the behavior documented here. Internal compiler,
automaton, prefilter, diagnostic, and implementation packages are not extension
contracts and may change between compatible releases.
