package no.rmz.rmatch.utils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;

/** Simple utility action used to count the number of something. */
public final class CounterAction implements Action {

  /** Our dear old Log. */
  private static final Logger LOG = Logger.getLogger(CounterAction.class.getName());

  /** Time between reports in milliseconds (~2 seconds). */
  private static final long REPORT_INTERVAL_MILLIS = 2000;

  /** Global rate limiter shared across ALL CounterAction instances. */
  private static final AtomicBoolean globalLoggingLock = new AtomicBoolean(false);
  
  /** Global timestamp for last log shared across ALL instances. */
  private static volatile long globalLastTick = System.currentTimeMillis();

  /** The value of the counter for THIS instance. */
  private final AtomicInteger counter = new AtomicInteger(0);

  @Override
  public void performMatch(final Buffer b, final int start, final int end) {
    counter.incrementAndGet();
    
    // LOGGING COMPLETELY DISABLED - no more INFO spam
    // If you need logging, uncomment the code below and adjust the interval
    /*
    final long now = System.currentTimeMillis();
    
    // Global rate limiting: only ONE CounterAction instance across the entire JVM can log at a time,
    // and only if enough time has passed since the last global log
    if (now - globalLastTick >= REPORT_INTERVAL_MILLIS && 
        globalLoggingLock.compareAndSet(false, true)) {
      
      try {
        // Double-check the time condition after acquiring the global lock
        if (now - globalLastTick >= REPORT_INTERVAL_MILLIS) {
          // Collecting a report from the known counters
          final StringBuilder sb = new StringBuilder();
          for (final Counter c : FastCounters.getCounters()) {
            sb.append("  ").append(c.toString()).append(", ");
          }

          final long duration = now - globalLastTick;

          LOG.log(
              Level.INFO,
              "Match counter == {0}, duration = {1}, start/end = {2}/{3}, match string = ''{4}'' {5}",
              new Object[] {
                currentCount, duration, start, end, b.getString(start, end + 1), sb.toString()
              });
          
          // Update the global timestamp
          globalLastTick = now;
        }
      } finally {
        // Always release the global logging lock
        globalLoggingLock.set(false);
      }
    }
    */
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
