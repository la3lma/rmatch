/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.rmz.rmatch.performancetests;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static no.rmz.rmatch.performancetests.BenchmarkLargeCorpus.getStringBuilderFromFileContent;
import static no.rmz.rmatch.performancetests.BenchmarkLargeCorpus.readRegexpsFromFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * This test is intended to replicate the behavior that when running against the
 * dostoyevsky corpus, we don't seem to find any matches for "laden", which is weird.
 */
@ExtendWith(MockitoExtension.class)
public class LadenDenAnomalityTest {

    private static MicroLogger log = new MicroLogger(new File("findMinimalRegexpSetLog.txt"));


    /**
     * Mocked action. Used to check that matches are found
     * in the right locations.
     */
    @Mock
    Action denAction;

    @Mock
    Action ladenAction;

    @Mock
    Action defaultAction;


    /**
     * A test article, the matcher implementation.
     */
    private Matcher m;

    private List<String> regexps;

    private Buffer buffer;
    private String corpus;

    /**
     * Instantiate test articles and set up the compiler mock
     * to simulate proper compilation of "ab" and "ac".
     */
    @BeforeEach
    public void setUp() {
        this.m = new MatcherImpl();
        final String[] corpusPaths = new String[]{"corpus/crime-and-punishment-by-dostoyevsky.txt"};
        this.corpus = getStringBuilderFromFileContent(corpusPaths).toString();
        this.buffer = new no.rmz.rmatch.utils.StringBuffer(corpus);
        this.regexps = readRegexpsFromFile("corpus/unique-words.txt", 1400);

        // Restrict using numbers found by searching
        // this.regexps = this.regexps.subList(1077, 1118); // -> Test passing that should fail!

        System.out.println("regexp 1117 = " + this.regexps.get(1117));
        System.out.println("regexp 1118 = " + this.regexps.get(1118));
        System.out.println("regexp 1077 = " + this.regexps.get(1077));
        System.out.println("regexp 1306 = " + this.regexps.get(1306));
        System.out.println("regexp 1307 = " + this.regexps.get(1307));
        System.out.println("regexp 1308 = " + this.regexps.get(1308));

        this.regexps = this.regexps.subList(1077, 1307);
        var foo = new ArrayList<String>(1307 - 1077 + 1);
        foo.addAll(this.regexps);
        this.regexps = foo;

        this.regexps.remove(1302 - 1077);
    }


    private boolean ladenFailed(Collection<String> regexps) {

        if (!(rangeContains("den", regexps) && rangeContains("laden", regexps))) {
            return false;
        }

        final var localMatcher = new MatcherImpl();

        // Prepare
        final AtomicBoolean denWasRun = new AtomicBoolean(false);
        final AtomicBoolean ladenWasRun = new AtomicBoolean(false);
        this.buffer = new no.rmz.rmatch.utils.StringBuffer(corpus);
        try {
            for (var r : regexps) {
                switch (r) {
                    case "den":
                        localMatcher.add("den", new Action() {
                            @Override
                            public void performMatch(Buffer b, int start, int end) {
                                denWasRun.set(true);
                            }
                        });
                        break;
                    case "laden":
                        localMatcher.add("laden", new Action() {
                            @Override
                            public void performMatch(Buffer b, int start, int end) {
                                ladenWasRun.set(true);
                            }
                        });
                        break;
                    default:
                        localMatcher.add(r, defaultAction);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Act
        localMatcher.match(buffer);

        return denWasRun.get() && !ladenWasRun.get();
    }


    record Interval(int lower, int upper) {};

    private final static class BinarySearcher {
        private final int upperLimit;
        private final int lowerLimit;
        private final Predicate<Integer> upperLimitTester;
        private final Predicate<Integer> lowerLimitTester;

        BinarySearcher(int lowerLimit,
                       int upperLimit,
                       Predicate<Integer> upperLimitTester,
                       Predicate<Integer> lowerLimitTester) {
            this.upperLimit = upperLimit;
            this.lowerLimit = lowerLimit;
            this.upperLimitTester = upperLimitTester;
            this.lowerLimitTester = lowerLimitTester;
            if (lowerLimit > upperLimit) {
                throw new RuntimeException("Upper limit larger than lower limit");
            }
        }

        private int findLowerLimit() {
            return findLimit(lowerLimitTester, true);
        }

        public int findUpperLimit() {
            return findLimit(upperLimitTester, false);
        }

        public int findLimit(Predicate<Integer> tester, boolean direction) {
            log.println("findLimit direction = "  + direction);

            int high = upperLimit;
            int low = lowerLimit;

            // Binary search
            while (high - low > 3) {
                int pivot = low + ((high - low) / 2);
                log.println("   Binary searching (low, high, pivot) = ("  + low + ", "+ high + ", " + pivot + ")");

                if (tester.test(pivot) ^ direction) {
                    high = pivot;
                } else {
                    low = pivot;
                }
            }


            System.out.println("   Linear searching (low,high) = ("  + low + ", " + high + ")");
            // Linear search
            int result = -1;
            if (direction) {
                while (low < high) {
                    result = low;
                    log.println("   preliminary result ="  + result);

                    if (tester.test(low + 1)) {
                        low += 1;
                    } else {
                        break;
                    }
                }
            } else {
                // from high to low
                result = high;
                while (low < high) {
                    result = high;
                    log.println("   preliminary result ="  + result);

                    if (tester.test(high - 1)) {
                        high -= 1;
                    } else {
                        break;
                    }
                }
            }

            log.println("   findLimit result = "  + result);

            return result;
        }

        public Interval findInterval() {
            return new Interval(findLowerLimit(), findUpperLimit());
        }
    }

    private final static class MicroLogger {

        List<PrintStream> printStreams = new ArrayList<>();

        public MicroLogger(File outputFile) {
            printStreams.add(System.out);
            try {
                printStreams.add(new PrintStream(outputFile));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        public void println(String str) {
            for (var ps : printStreams) {
                ps.println(str);
            }
        }
    }


    /**
     * Test matching the two regexps concurrently.
     */
    @Test
    public final void findMinimalRegexpSet() throws RegexpParserException {

        AtomicInteger start = new AtomicInteger(1077);
        AtomicInteger end = new AtomicInteger(1118 /* this.regexps.size() - 1 */);
        boolean result = true;

        final BinarySearcher searcher;
        searcher = new BinarySearcher(start.get(), end.get(),
                new Predicate<Integer>() {
                    @Override
                    public boolean test(Integer upperLimit) {
                        return rangeIsValid(LadenDenAnomalityTest.this.regexps.subList(start.get(), upperLimit));
                    }
                },
                new Predicate<Integer>() {
                    @Override
                    public boolean test(Integer lowerLimit) {
                        return rangeIsValid(LadenDenAnomalityTest.this.regexps.subList(lowerLimit, end.get()));
                    }
                });

        final Interval interval = searcher.findInterval();
        start.set(interval.lower);
        end.set(interval.upper);

        // Just testing that this will indeed fail laden
        var sublist = new ArrayList<String>();
        sublist.addAll(regexps.subList(start.get(), end.get()));
        if (ladenFailed(sublist)) {
            log.println("Yes. this interval works!: " + start.get() + "," + end.get());
            for (int i = start.get() ; i <= end.get(); i++) {
                log.println(" i = " + i + ", regex = " + regexps.get(i));
            }
        } else {
            log.println("No. this interval does not works!: " + start.get() + "," + end.get());
        }


        log.println("Determined boundaries: start = " + start + ", end = " + end);
        log.println("Trying to remove individual regexps");

        for (int i = start.get(); i <= end.get(); i += 1) {
            var testlist = new ArrayList<String>();
            testlist.addAll(regexps.subList(start.get(), end.get()));
            testlist.remove(i - start.get());
            result = ladenFailed(testlist);
            if (result) {
                log.println("Success for removal of i = " + i + ", regex = " + this.regexps.get(i));
            } else {
                log.println(" ==> Failure for removal of i = " + i + ",  regex = " + this.regexps.get(i));
            }
        }
        System.out.println("Done.");
    }

    private static boolean rangeContains(String den, Collection<String> range) {
        return range.contains(den);
    }

    private boolean rangeIsValid(Collection<String> range) {
        return rangeContains("den", range) && rangeContains("laden", range) && ladenFailed(range);
    }

    @Test
    public final void ablationTest() {

        log.println("Pre result = " + ladenFailed(this.regexps));

        List<String>  removable = new ArrayList<>();

        int nextToRemove = 0;

        List<String> current = null;

        while (current == null || nextToRemove < current.size()) {

            current = new ArrayList<>();
            current.addAll(this.regexps);
            current.removeAll(removable);

            String regexUnderTest = current.get(nextToRemove);
            current.remove(nextToRemove);
            if (!ladenFailed(current)) {
                log.println(" => Could not remove " + regexUnderTest);
                nextToRemove += 1;
            } else {
                log.println("Could remove " + regexUnderTest);
                removable.add(regexUnderTest);
            }
        }

        current = new ArrayList<>();
        current.addAll(this.regexps);
        current.removeAll(removable);

        boolean result = ladenFailed(current);
        log.println("Post Result = " + result);


        log.println("Minimal set of regexps that will pass the ladenFailed test:");
        for(var r: current) {
            log.println(" --> " + r);
        }
    }


    /**
     * Test matching the two regexps concurrently.
     */
    @Test
    public final void minimalBugReplicatingTest() throws RegexpParserException {

        // Prepare
        this.regexps = new ArrayList<>();
        this.regexps.add("den");
        this.regexps.add("laden");
        this.regexps.add("ll");

        this.buffer = new no.rmz.rmatch.utils.StringBuffer("laden");

        boolean result = ladenFailed(this.regexps);
        log.println("Pre Result = " + result);

        for (var r : this.regexps) {
            switch (r) {
                case "den":
                    m.add("den", denAction);
                    break;
                case "laden":
                    m.add("laden", ladenAction);
                    break;
                default:
                    m.add(r, defaultAction);
            }
        }

        // Act
        m.match(buffer);

        verify(ladenAction).performMatch(any(Buffer.class), eq(128310), eq(128314));
        verify(denAction).performMatch(any(Buffer.class),   eq(128312), eq(128314));

        log.println("Post Result = " + ladenFailed(this.regexps));
    }



    /**
     * Test matching the two regexps concurrently.
     */
    @Test
    public final void testUseOfOrdinaryMatcherOn1500SubsetOfRegexps() throws RegexpParserException {

        // Prepare
        for (var r : this.regexps) {
            switch (r) {
                case "den":
                    m.add("den", denAction);
                    break;
                case "laden":
                    m.add("laden", ladenAction);
                    break;
                default:
                    m.add(r, defaultAction);
            }
        }

        // Act
        m.match(buffer);

        // Verify
        verify(ladenAction).performMatch(any(Buffer.class), eq(128310), eq(128314));
        verify(denAction).performMatch(any(Buffer.class),   eq(128312), eq(128314));
    }
}
