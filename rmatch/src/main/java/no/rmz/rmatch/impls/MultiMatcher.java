/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.impls;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A multithreaded matcher. It will keep an array of matchers, into which it
 * will partition the regular expressions. Addition/Removal goes to one of the
 * matchers, not all of them. When running matches, all of the matchers are run
 * concurrently, and the MultiMatcher's implementation of the match method will
 * not complete until all of the matcher's match methods has completed.
 */
public final class MultiMatcher implements Matcher {

    /**
     * A simple guard against absolutely useless values of matchers. Now, 10K is
     * probably way too high for present day architectures, but one has
     * ambitions.
     */
    private static final int MAX_NO_OF_MATCHERS = 10000;
    /**
     * Look up the CPU/Cores/Memory configuration of the computer on which we
     * are running, and then use some heuristic to figure out an optimal number
     * of partitions to use.
     *
     * @return the number of partitions to use
     */
    private static int divineOptimalNumberOfMatchers() {
        final int cores = Runtime.getRuntime().availableProcessors();
        return cores;
    }

    /**
     * An array of matchers that are used when matching.
     */
    private final Matcher[] matchers;
    /**
     * The number of matchers in the matchers array.
     */
    private final int noOfMatchers;
    /**
     * The compiler used by all the matchers.
     */
    private final NDFACompiler compiler;
    /**
     * The regular expression factory used by all the matchers.
     */
    private final RegexpFactory regexpFactory;
    /**
     * An executor service that is used when invoking the sub-matchers.
     */
    private final ExecutorService executorService;

    /**
     * Create a new instance of the MultiMatcher.
     *
     * @param compiler The compiler used by all the matchers.
     * @param regexpFactory The regular expression factory used by all the
     * matchers.
     */
    public MultiMatcher(
            final NDFACompiler compiler,
            final RegexpFactory regexpFactory) {
        this(divineOptimalNumberOfMatchers(), compiler, regexpFactory);
    }

    /**
     * Create a new instance of the MultiMatcher.
     *
     * @param noOfMatchers The number of matchers in the matchers array.
     * @param compiler The compiler used by all the matchers.
     * @param regexpFactory The regular expression factory used by all the
     * matchers.
     */
    public  MultiMatcher(
            final int noOfMatchers,
            final NDFACompiler compiler,
            final RegexpFactory regexpFactory) {


        this.compiler = checkNotNull(compiler);
        this.regexpFactory = checkNotNull(regexpFactory);
        checkArgument(noOfMatchers >= 1, "No of partitions must be positive");
        checkArgument(noOfMatchers < MAX_NO_OF_MATCHERS,
                "No of partitions must be less than 100K");
        this.noOfMatchers = noOfMatchers;

        executorService = Executors.newFixedThreadPool(noOfMatchers);

        /**
         * Set up a set of partition into which we can pour regexps.
         */
        matchers = IntStream.range(0, noOfMatchers)
                .mapToObj(i -> new MatcherImpl(compiler, regexpFactory))
                .toArray(Matcher[]::new);
    }

    /**
     * Given a regular expression string, figure out which matcher to use.
     * Currently that calculation is based on the hash value of the string, but
     * in the future that may change.
     *
     * @param regexpString A string.
     * @return An integer in the range [0, noOfMatchers]
     */
    private Matcher getMatcher(final String regexpString) {
        checkNotNull(regexpString);
        final long hash = regexpString.hashCode()
                + (long) Integer.MAX_VALUE + 1;
        final int index = (int) (hash % noOfMatchers);

        return matchers[index];
    }

    @Override
    public void add(final String r, final Action a)
            throws RegexpParserException {
        getMatcher(r).add(r, a);
    }

    @Override
    public void remove(final String r, final Action a) {
        getMatcher(r).remove(r, a);
    }

    @Override
    public void match(final Buffer b) {
        assert (matchers.length == noOfMatchers);

        final CountDownLatch counter =
                new CountDownLatch(matchers.length);
        for (final Matcher matcher : matchers) {

            final Runnable runnable = () -> {
                matcher.match(b.clone());
                counter.countDown();
            };

            executorService.execute(runnable);
        }
        try {
            counter.await();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void shutdown() throws InterruptedException {
        for (final Matcher matcher : matchers) {
            matcher.shutdown();
        }
        executorService.shutdown();
        //noinspection ResultOfMethodCallIgnored
        executorService.awaitTermination(3, TimeUnit.SECONDS);
    }

    @Override
    public NodeStorage getNodeStorage() {
        // XXX This is wrong, since it only returns a subset of the
        //     nodes the multimatcher is using, but it's better than
        //     nothing if the objective is just to get a big graph to
        //     show someone.
        return matchers[0].getNodeStorage();
    }
}
