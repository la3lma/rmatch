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

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.RegexpFactory;

/**
 * Factory for the recommended production matcher.
 *
 * <p>{@link #newMatcher()} returns a matcher sized for the current machine. On systems with more
 * than two available processors this is a partitioned {@link MultiMatcher}, which can invoke
 * actions concurrently. Use {@link MatcherImpl} directly if a single-engine matcher is preferred
 * for a small example, deterministic debugging, or custom lifecycle control.
 */
public class MatcherFactory {

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

    final int noOfPartitions;
    if (AVAILABLE_PROCESSORS > 2) {
      noOfPartitions = (int) (AVAILABLE_PROCESSORS * 1.5);
    } else {
      noOfPartitions = 1;
    }

    return new MultiMatcher(
        noOfPartitions, new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
  }

  /**
   * Return the number of partitions that {@link #newMatcher()} would use on this machine.
   *
   * @return number of matcher partitions used by the default matcher
   */
  public static int getDefaultPartitionCount() {
    if (AVAILABLE_PROCESSORS > 2) {
      return (int) (AVAILABLE_PROCESSORS * 1.5);
    } else {
      return 1;
    }
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
