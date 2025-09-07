package no.rmz.rmatch.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/** Utility class to produce counters that are uniquely named (within the VM). */
public final class Counters {

  /** We implement the singleton pattern, and this is the instance. */
  private static final Counters INSTANCE = new Counters();

  /**
   * Get a new counter, uniquely named.
   *
   * @param name the name of the counter to create.
   * @param canBeDecremented
   * @return The newly created counter.
   */
  public static Counter newCounter(final String name, final boolean canBeDecremented) {
    return INSTANCE.privateNewCounter(name, canBeDecremented);
  }

  public static Counter newCounter(final String name) {
    return INSTANCE.privateNewCounter(name, false);
  }

  /**
   * Get a collection of counters.
   *
   * @return All the known counters.
   */
  public static Collection<Counter> getCounters() {
    return INSTANCE.privateGetCounters();
  }

  /** Dump the counters to stdout. */
  public static void dumpCounters() {
    INSTANCE.privatedumpCounters();
  }

  /** A map mapping strings to counters. */
  private final Map<String, Counter> counters = new TreeMap<>();

  /** Since this is an utility class we can't have public constructor. */
  private Counters() {}

  /** Dump the counters to stdout. */
  public void privatedumpCounters() {
    for (final Entry<String, Counter> entry : counters.entrySet()) {}
  }

  /**
   * Generate a new counter and put it into the counters map. Throws IllegalStateException if the
   * name is already registered.
   *
   * @param name The name of the counter.
   * @return a counter.
   */
  private Counter privateNewCounter(final String name, boolean canBeDecremented) {
    synchronized (counters) {
      if (counters.containsKey(name)) {
        throw new IllegalStateException("Attempt to get two counters with name " + name);
      } else {
        final Counter result = new Counter(name, canBeDecremented);
        counters.put(name, result);
        return result;
      }
    }
  }

  private Counter privateNewCounter(String name) {
    return privateNewCounter(name, false);
  }

  /**
   * Get all of the counters.
   *
   * @return a collection of counters.
   */
  private Collection<Counter> privateGetCounters() {
    synchronized (counters) {
      return counters.values();
    }
  }
}
