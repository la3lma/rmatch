package no.rmz.rmatch.performancetests.utils;

/**
 * Used by the FileInhaler. For each line that is read, a collector instance is invoked on the input
 * line.
 */
public interface Collector {

  /**
   * Add an input line to a collector.
   *
   * @param strLine an input line.
   */
  void add(final String strLine);
}
