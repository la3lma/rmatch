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
import static no.rmz.rmatch.internal.Checks.checkNotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.*;

/**
 * Partitioned matcher that scans with several {@link MatcherImpl} instances in parallel.
 *
 * <p>{@code MultiMatcher} distributes registered expressions across partitions using the expression
 * string's hash. During {@link #match(Buffer)}, each partition scans an independent clone of the
 * input buffer. The method returns only after all partitions finish, and any failure from a
 * partition is rethrown to the caller.
 *
 * <p>Because partitions run concurrently, actions registered with this matcher must be thread-safe.
 * Most application code should create instances through {@link MatcherFactory#newMatcher()} rather
 * than selecting partition counts directly.
 */
public final class MultiMatcher implements Matcher {

  /**
   * A simple guard against absolutely useless values of matchers. Now, 10K is probably way too high
   * for present day architectures, but one has ambitions.
   */
  private static final int MAX_NO_OF_MATCHERS = 10000;

  /**
   * Look up the CPU/Cores/Memory configuration of the computer on which we are running, and then
   * use some heuristic to figure out an optimal number of partitions to use.
   *
   * @return the number of partitions to use
   */
  private static int divineOptimalNumberOfMatchers() {
    return Runtime.getRuntime().availableProcessors();
  }

  /** An array of matchers that are used when matching. */
  private final Matcher[] matchers;

  /** The number of matchers in the matchers array. */
  private final int noOfMatchers;

  /** An executor service that is used when invoking the sub-matchers. */
  private final ExecutorService executorService;

  /**
   * Create a partitioned matcher using the runtime's default partition heuristic.
   *
   * @param compiler compiler used by all partitions
   * @param regexpFactory regular-expression factory used by all partitions
   */
  public MultiMatcher(final NDFACompiler compiler, final RegexpFactory regexpFactory) {
    this(divineOptimalNumberOfMatchers(), compiler, regexpFactory);
  }

  /**
   * Create a partitioned matcher with an explicit partition count.
   *
   * @param noOfMatchers number of matcher partitions to create
   * @param compiler compiler used by all partitions
   * @param regexpFactory regular-expression factory used by all partitions
   */
  public MultiMatcher(
      final int noOfMatchers, final NDFACompiler compiler, final RegexpFactory regexpFactory) {

    /** The compiler used by all the matchers. */
    NDFACompiler compiler1 = checkNotNull(compiler);
    /** The regular expression factory used by all the matchers. */
    RegexpFactory regexpFactory1 = checkNotNull(regexpFactory);
    checkArgument(noOfMatchers >= 1, "No of partitions must be positive");
    checkArgument(noOfMatchers < MAX_NO_OF_MATCHERS, "No of partitions must be less than 100K");
    this.noOfMatchers = noOfMatchers;

    executorService = Executors.newFixedThreadPool(noOfMatchers);

    /** Set up a set of partition into which we can pour regexps. */
    matchers =
        IntStream.range(0, noOfMatchers)
            .mapToObj(i -> new MatcherImpl(compiler, regexpFactory))
            .toArray(Matcher[]::new);
  }

  /**
   * Given a regular expression string, figure out which matcher to use. Currently that calculation
   * is based on the hash value of the string, but in the future that may change.
   *
   * @param regexpString A string.
   * @return An integer in the range [0, noOfMatchers]
   */
  private Matcher getMatcher(final String regexpString) {
    checkNotNull(regexpString);
    final long hash = regexpString.hashCode() + (long) Integer.MAX_VALUE + 1;
    final int index = (int) (hash % noOfMatchers);

    return matchers[index];
  }

  /**
   * Register an expression/action pair in one partition.
   *
   * @param r regular-expression text in the supported rmatch syntax subset
   * @param a action to run for each match
   * @throws RegexpParserException if {@code r} cannot be parsed
   */
  @Override
  public void add(final String r, final Action a) throws RegexpParserException {
    getMatcher(r).add(r, a);
  }

  /**
   * Remove an expression/action pair from its partition.
   *
   * @param r regular-expression text previously registered with this matcher
   * @param a action previously associated with {@code r}
   */
  @Override
  public void remove(final String r, final Action a) {
    getMatcher(r).remove(r, a);
  }

  /**
   * Scan the supplied buffer concurrently across all partitions.
   *
   * <p>Each partition receives an independent clone of {@code b}. Actions may run concurrently on
   * worker threads.
   *
   * @param b input buffer to scan
   */
  @Override
  public void match(final Buffer b) {
    assert (matchers.length == noOfMatchers);

    final CountDownLatch counter = new CountDownLatch(matchers.length);
    // If a partition fails we must still count down the latch, otherwise
    // match() hangs forever instead of failing. The first failure is
    // recorded and rethrown after all partitions have finished.
    final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    for (final Matcher matcher : matchers) {

      final Runnable runnable =
          () -> {
            try {
              matcher.match(b.clone());
            } catch (final Throwable t) {
              firstFailure.compareAndSet(null, t);
            } finally {
              counter.countDown();
            }
          };

      executorService.execute(runnable);
    }
    try {
      counter.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }

    final Throwable failure = firstFailure.get();
    if (failure != null) {
      if (failure instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (failure instanceof Error error) {
        throw error;
      }
      throw new RuntimeException("Matcher partition failed", failure);
    }
  }

  /**
   * Shut down all partition matchers and their worker pool.
   *
   * @throws InterruptedException if interrupted while waiting for worker termination
   */
  @Override
  public void shutdown() throws InterruptedException {
    for (final Matcher matcher : matchers) {
      matcher.shutdown();
    }
    executorService.shutdown();
    //noinspection ResultOfMethodCallIgnored
    executorService.awaitTermination(3, TimeUnit.SECONDS);
  }
}
