package no.rmz.rmatch.performancetests.misc;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.performancetests.utils.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MultiJavaRegexpMatchDetector implements MatchDetector {

    /**
     * A collector of worker processes that should be used when running the
     * matches.
     */
    private final Collection<Callable<Object>> matchers = new LinkedList<>();

    /**
     * An executor service that will be used to run all the matchers. It should
     * have enough threads to never run out of cores that can do stuff.
     */
    private final ExecutorService es;

    private final LineSource linesource;

    public MultiJavaRegexpMatchDetector(final LineSource linesource) {
        // Don't know what an optimal number is.
        es = Executors.newFixedThreadPool(10);
        this.linesource = linesource;
    }

    /**
     * Run all the matchers on an input line.
     *
     */
    @Override
    public void detectMatchesForCurrentLine() {
        try {
            es.invokeAll(matchers);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void add(final String rexpString, final Action actionToRun) {
        final Pattern pattern;
        pattern = Pattern.compile(rexpString);
        final java.util.regex.Matcher rmatcher = pattern.matcher("");

        final Callable<Object> matchPerformer = makeMatchPerformer(linesource, rmatcher, actionToRun);
        matchers.add(matchPerformer);
    }

    public final static Callable<Object> makeMatchPerformer(
            final LineSource linesource,
            final Matcher rmatcher,
            final Action actionToRun) {
        final Callable<Object> callable = () -> {
            rmatcher.reset(linesource.getCurrentLine());
            if (rmatcher.find()) {
                // We'll see about this
                actionToRun.performMatch(null, -1, -1);
            }
            return null;
        };
        return callable;
    }

    /**
     * The main method.
     *
     * @param argv Command line arguments. If present, arg 1 is interpreted as a
     * maximum limit on the number of regexps to use.
     * @throws RegexpParserException when things go bad.
     */
    public static void main(final String[] argv) throws RegexpParserException {
        System.out.println("MultiJavaRegexpMatchDetector");
        final LineSource lineSource = new LineSource();
        final MatchDetector matchDetector =
                new MultiJavaRegexpMatchDetector(lineSource);
        final LineMatcher lineMatcher =
                new LineMatcher(lineSource, matchDetector);

        final JavaRegexpTester matcher = new JavaRegexpTester(lineMatcher);
        final Buffer b = new WutheringHeightsBuffer("corpus/wuth10-one-word-per-line.txt");
        final String [] brgv;
        if (argv == null || argv.length == 0) {
            brgv = new String[] {"100"};
        } else {
            brgv = argv;
        }
        MatcherBenchmarker.testMatcher(b, matcher, brgv);
        System.exit(0);
    }
}
