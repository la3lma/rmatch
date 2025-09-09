package no.rmz.rmatch.performancetests.misc;

import static no.rmz.rmatch.performancetests.misc.MultiJavaRegexpMatchDetector.makeMatchPerformer;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.performancetests.utils.*;

public class LongJavaRegexpMatchDetector implements MatchDetector {

  private static final Logger LOG = Logger.getLogger(LongJavaRegexpMatchDetector.class.getName());

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

  private LineSource linesource;

  /**
   * A state parameter for the class. When in matching state it is illegal to add more regexp/action
   * pairs.
   */
  private boolean matching = false;

  public LongJavaRegexpMatchDetector(final LineSource linesource) {
    // Don't know what an optimal number is.
    es = Executors.newFixedThreadPool(10);
    this.linesource = linesource;
  }

  /** Make a matcher per action. */
  private void makeMatchers() {
    for (final Entry<Action, Set<String>> entry : actionToRegexpMap.entrySet()) {
      final Action action = entry.getKey();
      final Set<String> regexps = entry.getValue();
      final StringBuilder sb = new StringBuilder();
      String separator = "";
      for (final String r : regexps) {
        sb.append(separator);
        sb.append(r);
        separator = "|";
      }

      final String rexpString = sb.toString();
      LOG.info("String  = " + rexpString);

      final Pattern pattern;
      pattern = Pattern.compile(rexpString);
      final java.util.regex.Matcher rmatcher = pattern.matcher("");

      final Callable<Object> matchPerformer = makeMatchPerformer(linesource, rmatcher, action);
      matchers.add(matchPerformer);
    }
  }

  /**
   * Run all the matchers on an input line.
   *
   * @param input
   */
  @Override
  public void detectMatchesForCurrentLine() {
    if (!matching) {
      makeMatchers();
      matching = true;
    }
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

  /**
   * The main method.
   *
   * @param argv Command line arguments. If present, arg 1 is interpreted as a maximum limit on the
   *     number of regexps to use.
   * @throws RegexpParserException when things go bad.
   */
  public static void main(final String[] argv) throws RegexpParserException {
    final LineSource lineSource = new LineSource();
    final MatchDetector matchDetector = new LongJavaRegexpMatchDetector(lineSource);
    final LineMatcher lineMatcher = new LineMatcher(lineSource, matchDetector);

    final JavaRegexpTester matcher = new JavaRegexpTester(lineMatcher);

    final Buffer b = new WutheringHeightsBuffer("corpus/wuth10-one-word-per-line.txt");
    MatcherBenchmarker.testMatcher(b, matcher, argv);
    LOG.info("No of lines read was " + lineMatcher.getNoOfLines());
  }
}
