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

import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.utils.RegexStringBuffer;

/**
 * Convenience entry point for the supported rmatch public API.
 *
 * <p>Use this class when ordinary application code needs a matcher or a string-backed input buffer.
 * The implementation packages remain deliberately outside the JPMS export surface so that rmatch
 * can keep improving its compiler and engine internals without making those implementation details
 * part of the compatibility promise.
 */
public final class RMatch {

  /**
   * Create the recommended matcher for this runtime.
   *
   * <p>On machines with several available processors this may return a partitioned matcher, which
   * can invoke match actions concurrently. Use {@link #newSingleMatcher()} when deterministic
   * single-engine execution is more important than throughput.
   *
   * @return new matcher instance ready for pattern registration
   */
  public static Matcher newMatcher() {
    return MatcherFactory.newMatcher();
  }

  /**
   * Create a single-engine matcher.
   *
   * <p>This is a good fit for small examples, deterministic debugging, and callers that do not want
   * action callbacks from multiple worker threads.
   *
   * @return new single-engine matcher instance
   */
  public static Matcher newSingleMatcher() {
    return MatcherFactory.newSingleMatcher();
  }

  /**
   * Adapt a {@link String} to the {@link Buffer} interface consumed by matchers.
   *
   * @param text input text to scan
   * @return independent buffer over {@code text}
   */
  public static Buffer buffer(final String text) {
    return new RegexStringBuffer(text);
  }

  private RMatch() {}
}
