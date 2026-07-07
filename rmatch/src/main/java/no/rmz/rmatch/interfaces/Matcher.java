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
package no.rmz.rmatch.interfaces;

import no.rmz.rmatch.compiler.RegexpParserException;

/**
 * Main public API for registering regular expressions and matching them against buffers.
 *
 * <p>A matcher is normally used in three steps:
 *
 * <ol>
 *   <li>Register one or more patterns with {@link #add(String, Action)}.
 *   <li>Call {@link #match(Buffer)} for each input buffer that should be scanned.
 *   <li>Call {@link #shutdown()} when the matcher is no longer needed.
 * </ol>
 *
 * <p>rmatch is designed for workloads where many patterns are reused against large inputs. For a
 * single small pattern and a single small string, {@code java.util.regex} is usually simpler.
 *
 * <p>The regular-expression syntax is intentionally a supported subset of Java regular expressions;
 * see the project README for the current list. Match callbacks receive inclusive start/end offsets,
 * while {@link Buffer#getString(int, int)} uses Java substring-style exclusive end offsets.
 */
public interface Matcher {

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
   */
  void add(final String r, final Action a) throws RegexpParserException;

  /**
   * Remove one association between a regular expression and an action.
   *
   * <p>If the same expression has several actions, only the supplied expression/action pair is
   * removed. Removing a pair that is not present is a no-op.
   *
   * @param r regular-expression text previously registered with {@link #add(String, Action)}
   * @param a action previously associated with {@code r}
   */
  void remove(final String r, final Action a);

  /**
   * Scan the supplied buffer with all currently registered expressions.
   *
   * <p>Actions are invoked during the scan. Match callbacks receive the same buffer instance (or a
   * clone of it for partitioned implementations) plus inclusive start/end offsets for the matched
   * text.
   *
   * @param b input buffer to scan; callers normally use {@code new RegexStringBuffer(text)}
   */
  void match(final Buffer b);

  /**
   * Return the internal node storage used by this matcher.
   *
   * <p>This method is mainly useful for diagnostics and graph/debug tooling. It is not needed for
   * normal matching.
   *
   * @return internal node storage for this matcher
   */
  NodeStorage getNodeStorage();

  /**
   * Release resources owned by the matcher.
   *
   * <p>Single-threaded implementations may have nothing to do. Partitioned implementations use
   * worker threads and should be shut down when the matcher is no longer needed.
   *
   * @throws InterruptedException if shutdown waits for worker threads and is interrupted
   */
  void shutdown() throws InterruptedException;
}
