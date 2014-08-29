package no.rmz.rmatch.performancetests.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NodeStorage;

/**
 * Will pick up all the regexps located in the regexps file, create a Java
 * Regexp instance for each of them. It will then run all of the input lines
 * from the corpus through all of the regexps counting all of the matches.
 *
 * The process is multithreaded so we shouldn't get into a congestion until
 * we're really short of CPU power.
 */
public final class JavaRegexpTester implements Matcher {

    /**
     * The test article is represented as a LineMatcher,
     * a simplified matcher that matches line by line.   The
     * LineMatcher must have a LineSource associated to it,
     * and the LineMatcher feeds it with lines that's being
     * read from the input Buffer.  Got that?
     */
    final LineMatcher lineMatcher;

    public JavaRegexpTester(final LineMatcher lineMatcher) {
        this.lineMatcher = checkNotNull(lineMatcher);
    }

    @Override
    public void add(final String rexpString, final Action actionToRun) {
        checkNotNull(rexpString);
        checkNotNull(actionToRun);
        lineMatcher.add(rexpString, actionToRun);
    }

    @Override
    public void remove(final String rexpString, final Action actionToRun) {
        checkNotNull(rexpString);
        checkNotNull(actionToRun);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void match(final Buffer b) {
        lineMatcher.match(b);
    }

    @Override
    public NodeStorage getNodeStorage() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void shutdown() throws InterruptedException {
    }
}
