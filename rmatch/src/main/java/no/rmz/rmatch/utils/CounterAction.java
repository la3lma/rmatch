package no.rmz.rmatch.utils;

import java.util.logging.Level;
import java.util.logging.Logger;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;

/** Simple utility action used to count the number of something. */
public final class CounterAction implements Action {

  /** Our dear old Log. */
  private static final Logger LOG = Logger.getLogger(CounterAction.class.getName());

  /** How long to wait between reports. */
  private static final int DEFAULT_TICK_INTERVAL_FOR_IN_ACTIONS_BETWEEN_REPORTS = 4000;

  /** Should the reports be verbose? */
  private final boolean verbose = true;

  /** Monitor used to synchronize access. */
  private final Object monitor = new Object();

  /** The value of the counter. */
  private int counter = 0;

  /** The tick interval we'll actually use. */
  private final int tickInterval = DEFAULT_TICK_INTERVAL_FOR_IN_ACTIONS_BETWEEN_REPORTS;

  /** Initialize the timestamp for the last tick to be the time of class initialization. */
  private long lastTick = System.currentTimeMillis();

  @Override
  public void performMatch(final Buffer b, final int start, final int end) {
    synchronized (monitor) {
      counter += 1;

      if (verbose && (counter % tickInterval) == 0) {

        // Collecting a report from the known counters
        final StringBuilder sb = new StringBuilder();
        for (final Counter c : FastCounters.getCounters()) {
          sb.append("  ").append(c.toString()).append(", ");
        }

        // Making a report for the current counter,
        // plus all the counters.
        long now = System.currentTimeMillis();
        long duration = now - lastTick;
        double speed = duration / (double) tickInterval;

        LOG.log(
            Level.INFO,
            "Match counter == {0}, duration = {1}, speed = {2} millis/tick, start/end = {3}/{4}, match string = ''{5}'' {6}",
            new Object[] {
              counter, duration, speed, start, end, b.getString(start, end + 1), sb.toString()
            });
        lastTick = now;
      }
    }
  }

  /**
   * Return the number of matches that has been performed on this action.
   *
   * @return an integer.
   */
  public int getCounter() {
    synchronized (monitor) {
      return counter;
    }
  }
}
