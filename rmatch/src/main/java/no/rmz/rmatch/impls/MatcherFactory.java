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
package no.rmz.rmatch.impls;

import static no.rmz.rmatch.internal.Checks.checkArgument;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.interfaces.RegexpFactory;

/**
 * Factory for the recommended production matcher.
 *
 * <p>{@link #newMatcher()} returns a matcher sized for the current machine. On systems with more
 * than two available processors this is a partitioned matcher, which can invoke actions
 * concurrently. Use {@link #newSingleMatcher()} when deterministic single-engine execution is
 * preferred.
 */
public final class MatcherFactory {

  /** Largest supported explicit matcher parallelism. */
  static final int MAX_PARALLELISM = 1024;

  /** A management bean that we use to probe the execution environment. */
  private static final OperatingSystemMXBean OS_MBEAN =
      ManagementFactory.getOperatingSystemMXBean();

  /** The number of processors available to us. */
  private static final int AVAILABLE_PROCESSORS = OS_MBEAN.getAvailableProcessors();

  /**
   * Create the recommended matcher for this runtime.
   *
   * <p>The current heuristic uses one partition on very small machines and roughly 1.5 times the
   * available processor count otherwise. Each call returns a new matcher with an independent
   * pattern set and lifecycle.
   *
   * @return new matcher instance ready for pattern registration
   */
  public static Matcher newMatcher() {
    return newMatcher(getDefaultPartitionCount());
  }

  /**
   * Create a matcher with an explicit number of pattern partitions and concurrent workers.
   *
   * @param parallelism requested matcher parallelism
   * @return new matcher instance ready for pattern registration
   * @throws IllegalArgumentException if {@code parallelism} is outside the supported range
   */
  public static Matcher newMatcher(final int parallelism) {
    checkArgument(parallelism >= 1, "Parallelism must be positive");
    checkArgument(parallelism <= MAX_PARALLELISM, "Parallelism must not exceed " + MAX_PARALLELISM);
    if (parallelism == 1) {
      return newSingleMatcher();
    }
    return new MultiMatcher(
        parallelism, new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
  }

  /**
   * Create a single-engine matcher.
   *
   * <p>This is useful for small examples, deterministic debugging, and callers that do not want
   * partitioned matching. Production throughput-oriented callers should normally prefer {@link
   * #newMatcher()}.
   *
   * @return new single-engine matcher instance
   */
  public static Matcher newSingleMatcher() {
    return new MatcherImpl();
  }

  /**
   * Return the number of partitions that {@link #newMatcher()} would use on this machine.
   *
   * @return number of matcher partitions used by the default matcher
   */
  public static int getDefaultPartitionCount() {
    return defaultParallelismFor(AVAILABLE_PROCESSORS);
  }

  static int defaultParallelismFor(final int availableProcessors) {
    checkArgument(availableProcessors >= 1, "Available processor count must be positive");
    if (availableProcessors <= 2) {
      return 1;
    }
    final long suggestedParallelism = availableProcessors * 3L / 2L;
    return (int) Math.min(suggestedParallelism, MAX_PARALLELISM);
  }

  /**
   * Return the number of processors reported by the JVM.
   *
   * @return available processor count used by the factory heuristic
   */
  public static int getAvailableProcessors() {
    return AVAILABLE_PROCESSORS;
  }

  private MatcherFactory() {}
}
