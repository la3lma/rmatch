package no.rmz.rmatch.performancetests;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;
import java.util.regex.Pattern;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;

/**
 * The intent of this class is to look for matches in the wuthering heights
 * corpus as it appears originally from the Gutenberg project. This means both
 * looking for matches within other matches and looking for overlapping matches.
 * This will mean a lot more work for the matcher and that will be unfair, which
 * is the whole point of the exercise. This kind of matching is something the
 * rmatch matcher should be much better at.
 *
 * XXXX: At present this is somewhat of an abomination. I expect that the
 * present class will have to be rewritten extensibly before it will produce
 * results that I have any confidence in.
 */
public class TestJavaRegexpUnfairly implements Matcher {

    private final static Logger LOG =
            Logger.getLogger(TestJavaRegexpUnfairly.class.getName());
    /**
     * The main method.
     *
     * @param argv Command line arguments. If present, arg 1 is interpreted as a
     * maximum limit on the number of regexps to use.
     * @throws RegexpParserException when things go bad.
     */
    public static void main(final String[] argv) throws RegexpParserException {
        
        final Matcher matcher = new TestJavaRegexpUnfairly();
        
        final Buffer b = new WutheringHeightsBuffer("corpus/wuthr10.txt");
        
        MatcherBenchmarker.testMatcher(b, matcher, argv);
        
        // Kill all threads and get out of here
        System.exit(0);
    }
    /**
     * A map that for all the actions we are managing, keeps track of all the
     * regexps that are triggered by that particular action.
     */
    private final Map<Action, Set<String>> actionToRegexpMap =
            new HashMap<>();
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
    /**
     * A state parameter for the class. When in matching state it is illegal to
     * add more regexp/action pairs.
     */
    private final boolean matching = false;

    public TestJavaRegexpUnfairly() {
        // Don't know what an optimal number is.
        es = Executors.newFixedThreadPool(10);
    }

    /**
     * Make a matcher per action.
     */
    private void makeMatchers(final Buffer b, final String strToSearchIn) {
        for (final Entry<Action, Set<String>> entry : actionToRegexpMap.entrySet()) {
            final Action action = entry.getKey();
            final Set<String> regexps = entry.getValue();

            for (final String rexp : regexps) {
                final Pattern pattern;
                pattern = Pattern.compile(rexp);

                // The "" is irrelevant, it could be anything.
                // For real matches we use the reset method below
                // anyway.
                final java.util.regex.Matcher rmatcher = pattern.matcher("");

                final Callable<Object> callable = new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        rmatcher.reset(strToSearchIn);
                        while (rmatcher.find()) {
                            final int start = rmatcher.start();
                            final int end = rmatcher.end();
                            action.performMatch(b, start, end);
                        }
                        return null;
                    }
                };
                matchers.add(callable);
            }
        }
    }

    @Override
    public void match(final Buffer b) {
        final WutheringHeightsBuffer ssb = (WutheringHeightsBuffer) b;
        makeMatchers(b, ssb.getCurrentRestString(0));
        try {
            es.invokeAll(matchers);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Set<String> getSetForAction(final Action action) {
        final Set<String> targetSet;
        if (!actionToRegexpMap.containsKey(action)) {
            targetSet = new HashSet<String>();
            actionToRegexpMap.put(action, targetSet);
        } else {
            targetSet = actionToRegexpMap.get(action);
        }
        return targetSet;
    }

    @Override
    public void add(
            final String rexpString,
            final Action actionToRun) {
        if (matching) {
            throw new RuntimeException("Can't add more when already matching");
        }
        final Set<String> targetSet = getSetForAction(actionToRun);

        targetSet.add(rexpString);
    }


    @Override
    public void remove(String r, Action a) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public NodeStorage getNodeStorage() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void shutdown() throws InterruptedException {
    }

}
