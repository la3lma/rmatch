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

/**
 * Callback invoked when a registered expression matches an input buffer.
 *
 * <p>Implement this interface to collect matches, update counters, emit records, or otherwise make
 * matches visible to application code. The callback receives the input buffer and half-open {@code
 * [start, end)} offsets, the same convention as {@link String#substring(int, int)}. To recover the
 * matched text, call {@code buffer.getString(start, end)}.
 *
 * <p><b>Thread-safety contract:</b> actions may be invoked concurrently from multiple engine worker
 * threads. In particular, {@code RMatch.newMatcher()} returns a partitioned matcher on multi-core
 * systems. An action instance, especially one shared between several expressions, must therefore be
 * prepared for concurrent {@link #performMatch(Buffer, long, long)} invocations. Use {@code
 * java.util.concurrent} types such as {@code LongAdder} or {@code AtomicLong}, a synchronized
 * block, or a concurrent collection for mutable state. A plain {@code int++} counter in an action
 * is a lost-update bug waiting to happen.
 *
 * <p><b>Exception contract:</b> exceptions thrown by an action are not swallowed. They abort the
 * scan that invoked the action and propagate out of {@link Matcher#match(Buffer)}; see that
 * method's documentation for the partitioned-matcher details. Actions that must not disturb the
 * scan should catch their own exceptions.
 */
public interface Action {

  /**
   * Handle a single match.
   *
   * <p>The match spans the half-open range {@code [start, end)}: {@code start} is inclusive and
   * {@code end} is exclusive, exactly like {@link String#substring(int, int)} and {@link
   * Buffer#getString(long, long)}. The matched text is therefore {@code b.getString(start, end)}
   * and its length is {@code end - start}.
   *
   * <p>This method may be called concurrently from multiple threads; see the thread-safety contract
   * in the class documentation.
   *
   * <p>Offsets are {@code long} to match {@link Buffer} positions, so custom buffer implementations
   * larger than the {@code int} range stay expressible.
   *
   * @param b buffer where the match occurred
   * @param start zero-based inclusive start offset of the match
   * @param end zero-based exclusive end offset of the match
   */
  void performMatch(final Buffer b, final long start, final long end);
}
