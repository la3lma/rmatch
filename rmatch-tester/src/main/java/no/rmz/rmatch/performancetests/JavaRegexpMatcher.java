package no.rmz.rmatch.performancetests;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NodeStorage;

/**
 * The intent of this class is to look for matches in the wuthering heights corpus as it appears
 * originally from the Gutenberg project. This means both looking for matches within other matches
 * and looking for overlapping matches. This will mean a lot more work for the matcher and that will
 * be unfair, which is the whole point of the exercise. This kind of matching is something the
 * rmatch matcher should be much better at.
 *
 * <p>XXXX: At present this is somewhat of an abomination. I expect that the present class will have
 * to be rewritten extensibly before it will produce results that I have any confidence in.
 */
public class JavaRegexpMatcher implements Matcher {

  private static final Logger LOG = Logger.getLogger(JavaRegexpMatcher.class.getName());

  /**
   * A map that for all the actions we are managing, keeps track of all the regexps that are
   * triggered by that particular action.
   */
  private final Map<Action, Set<String>> actionToRegexpMap = new HashMap<>();

  /** A collector of worker processes that should be used when running the matches. */
  private final Collection<Callable<Object>> matchers = new LinkedList<>();

  /**
   * An executor service that will be used to run all the matchers. It should have enough threads to
   * never run out of cores that can do stuff.
   */
  private final ExecutorService es;

  /**
   * A state parameter for the class. When in matching state it is illegal to add more regexp/action
   * pairs.
   */
  private final boolean matching = false;

  public JavaRegexpMatcher() {
    // Don't know what an optimal number is.  The heuristic
    // implemented below is that the threads processors are fake
    // hyperthreads, and the real number of cores is halve that,
    // so that's then number of executors we should go for.
    // It's a guess, it may be wrong.
    int cores = Runtime.getRuntime().availableProcessors() / 2;
    es = Executors.newFixedThreadPool(cores);
  }

  /** Make a matcher per action. */
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

        final Callable<Object> callable =
            () -> {
              rmatcher.reset(strToSearchIn);
              while (rmatcher.find()) {
                final int start = rmatcher.start();
                final int end = rmatcher.end() - 1;
                action.performMatch(b, start, end);
              }
              return null;
            };
        matchers.add(callable);
      }
    }
  }

  @Override
  public void match(final Buffer b) {
    makeMatchers(b, b.getCurrentRestString());
    try {
      es.invokeAll(matchers);
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex);
    }
  }

  private Set<String> getSetForAction(final Action action) {
    final Set<String> targetSet;
    if (!actionToRegexpMap.containsKey(action)) {
      targetSet = new HashSet<>();
      actionToRegexpMap.put(action, targetSet);
    } else {
      targetSet = actionToRegexpMap.get(action);
    }
    return targetSet;
  }

  @Override
  public void add(final String rexpString, final Action actionToRun) {
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
  public void shutdown() throws InterruptedException {}
}
