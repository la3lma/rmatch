package no.rmz.rmatch.performancetests.utils;

import no.rmz.rmatch.interfaces.Action;

public interface MatchDetector {

  void add(final String rexpString, final Action actionToRun);

  /**
   * Run all the matchers on an input line.
   *
   * @param input
   */
  void detectMatchesForCurrentLine();
}
