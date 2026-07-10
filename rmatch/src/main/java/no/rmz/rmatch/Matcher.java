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
package no.rmz.rmatch;

import java.util.Set;

/**
 * Main public API for registering regular expressions and matching them against buffers.
 *
 * <p>A matcher is normally used in three steps:
 *
 * <ol>
 *   <li>Register one or more patterns with {@link #add(String, Action)}.
 *   <li>Call {@link #match(Buffer)} for each input buffer that should be scanned.
 *   <li>Call {@link #close()} when the matcher is no longer needed, typically via
 *       try-with-resources.
 * </ol>
 *
 * <p>rmatch is designed for workloads where many patterns are reused against large inputs. For a
 * single small pattern and a single small string, {@code java.util.regex} is usually simpler.
 *
 * <p>The regular-expression syntax is intentionally a supported subset of Java regular expressions;
 * see the project README for the current list. Match callbacks receive half-open {@code [start,
 * end)} offsets, the same convention as {@link String#substring(int, int)} and {@link
 * Buffer#getString(long, long)}, so matched text is recovered with {@code buffer.getString(start,
 * end)}.
 *
 * <p><b>Threading contract:</b> a matcher instance is not a concurrent registry. Register patterns
 * first, then match; do not call {@link #add(String, Action)} or {@link #remove(String, Action)}
 * while a {@link #match(Buffer)} is in progress, and do not invoke {@link #match(Buffer)}
 * concurrently from several threads on the same instance. Alternating registration and matching
 * phases sequentially is fully supported. Partitioned implementations parallelize internally, so
 * callers rarely need concurrent access to the matcher object itself.
 *
 * <p><b>Lifecycle contract:</b> after {@link #close()}, every method except {@code close()} throws
 * {@link IllegalStateException}. Closing is idempotent.
 */
public interface Matcher extends AutoCloseable {

  /**
   * Register a regular expression and the action to invoke whenever that expression matches.
   *
   * <p>The same action may be registered for more than one expression. Implementations may invoke
   * actions concurrently, so action implementations must be thread-safe unless the caller knows the
   * concrete matcher is single-threaded.
   *
   * @param r regular-expression text in the rmatch supported syntax subset
   * @param a action to run for each match
   * @throws RegexpParserException if {@code r} cannot be parsed by the supported rmatch syntax
   * @throws IllegalStateException if the matcher has been closed
   */
  void add(final String r, final Action a) throws RegexpParserException;

  /**
   * Register a regular expression with per-pattern option flags.
   *
   * <p>Flags express the same semantics as the corresponding inline syntax; for example {@link
   * PatternFlag#CASE_INSENSITIVE} is equivalent to prefixing the pattern with {@code (?i)}. The
   * flagged registration is identified by the combination of pattern and flags, so a later {@link
   * #remove(String, Set, Action)} must supply the same flags.
   *
   * @param r regular-expression text in the rmatch supported syntax subset
   * @param flags per-pattern option flags; may be empty
   * @param a action to run for each match
   * @throws RegexpParserException if {@code r} cannot be parsed by the supported rmatch syntax
   * @throws IllegalStateException if the matcher has been closed
   */
  default void add(final String r, final Set<PatternFlag> flags, final Action a)
      throws RegexpParserException {
    add(PatternFlag.applyTo(r, flags), a);
  }

  /**
   * Remove one association between a regular expression and an action.
   *
   * <p>If the same expression has several actions, only the supplied expression/action pair is
   * removed. Removing a pair that is not present is a no-op.
   *
   * @param r regular-expression text previously registered with {@link #add(String, Action)}
   * @param a action previously associated with {@code r}
   * @throws IllegalStateException if the matcher has been closed
   */
  void remove(final String r, final Action a);

  /**
   * Remove an expression/action pair that was registered with flags.
   *
   * <p>The flags must equal the flags used at registration time.
   *
   * @param r regular-expression text previously registered with {@link #add(String, Set, Action)}
   * @param flags flags supplied when the pair was registered
   * @param a action previously associated with {@code r}
   * @throws IllegalStateException if the matcher has been closed
   */
  default void remove(final String r, final Set<PatternFlag> flags, final Action a) {
    remove(PatternFlag.applyTo(r, flags), a);
  }

  /**
   * Scan the supplied buffer with all currently registered expressions.
   *
   * <p>Actions are invoked during the scan. Match callbacks receive the same buffer instance plus
   * half-open {@code [start, end)} offsets for the matched text.
   *
   * <p>An exception thrown by an action is not swallowed: it aborts that scan and propagates out of
   * this method, so remaining matches in the aborted scan are not reported. In a partitioned
   * matcher only the failing partition aborts; the other partitions run to completion (their
   * actions are still invoked) and the first failure is then rethrown, unwrapped if it was a {@link
   * RuntimeException} or {@link Error}. A matcher that has thrown from {@code match} stays usable
   * for subsequent scans, but actions should treat "some matches were already delivered before the
   * failure" as the expected state.
   *
   * @param b input buffer to scan; callers normally use {@link RMatch#stringBuffer(String)}
   * @throws IllegalStateException if the matcher has been closed
   */
  void match(final Buffer b);

  /**
   * Release resources owned by the matcher.
   *
   * <p>Single-threaded implementations may have nothing to do. Partitioned implementations use
   * worker threads and must be closed when the matcher is no longer needed, otherwise those
   * non-daemon threads keep the JVM alive. Prefer try-with-resources.
   *
   * <p>Closing is idempotent, and it is the only method that may be called on a closed matcher; all
   * others throw {@link IllegalStateException}. If the calling thread is interrupted while waiting
   * for worker threads to terminate, implementations restore the interrupt flag and return.
   */
  @Override
  void close();
}
