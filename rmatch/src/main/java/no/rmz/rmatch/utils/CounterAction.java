package no.rmz.rmatch.utils;

import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Simple utility action used to count the number of something. */
public final class CounterAction implements Action {

  /** Our dear old Log. */
  private static final Logger LOG = Logger.getLogger(CounterAction.class.getName());

  /** Only try to log something every 10000 matches. */
  private static final int REPORT_INTERVAL = 20000;

  /** Time between reports in milliseconds (~2 seconds). */
  private static final long REPORT_INTERVAL_MILLIS = 2000;

  /** Global rate limiter shared across ALL CounterAction instances. */
  private static final AtomicBoolean globalLoggingLock = new AtomicBoolean(false);

  /** Global timestamp for last log shared across ALL instances. */
  private static volatile long globalLastTick = System.currentTimeMillis();

  /** Global position for last log shared across ALL instances. */
  private static volatile long globalLastPosition = 0;

  /** The value of the counter for THIS instance. */
  private final AtomicInteger counter = new AtomicInteger(0);

  @Override
  public void performMatch(final Buffer b, final int start, final int end) {
    int currentCount = counter.incrementAndGet();

    // This is the common case, only once in a very few times we will even attempt
    // to print something
    if ((currentCount % REPORT_INTERVAL) != 0) {
      return;
    }

    // The second criterion to check for if it has taken long enough time
    // since the last time we logged something.
    final long now = System.currentTimeMillis();

    // Global rate limiting: only ONE CounterAction instance across the entire JVM can log at a
    // time,
    // and only if enough time has passed since the last global log
    if (now - globalLastTick >= REPORT_INTERVAL_MILLIS
        && globalLoggingLock.compareAndSet(false, true)) {

      try {
        // Double-check the time condition after acquiring the global lock
        if (now - globalLastTick >= REPORT_INTERVAL_MILLIS) {
          // Collecting a report from the known counters
          final StringBuilder sb = new StringBuilder();
          for (final Counter c : FastCounters.getCounters()) {
            sb.append("  ").append(c.toString()).append(", ");
          }

          final long duration = now - globalLastTick;
          final long ticks = b.getCurrentPos() - globalLastPosition;
          globalLastPosition = b.getCurrentPos();
          double currentMillisPerTick = (double) duration / ticks;

          LOG.log(
              Level.INFO,
              "milliseconds/tick = {0}, duration = {1} millis, start/end = {2}/{3}, match string = ''{4}'' {5}",
              new Object[] {
                currentMillisPerTick,
                duration,
                start,
                end,
                b.getString(start, end + 1),
                sb.toString(),
              });

          // Update the global timestamp
          globalLastTick = now;
        }
      } finally {
        // Always release the global logging lock
        globalLoggingLock.set(false);
      }
    }
  }

  /**
   * Return the number of matches that has been performed on this action.
   *
   * @return an integer.
   */
  public int getCounter() {
    return counter.get();
  }
}
