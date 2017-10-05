package no.rmz.rmatch.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Utility class to produce counters that are uniquely named (within the VM).
 */
public final class Counters {

    /**
     * We implement the singelton pattern, and this is the instance.
     */
    private static final Counters INSTANCE = new Counters();

    /**
     * Get a new counter, uniquely named.
     *
     * @param name the name of the counter to create.
     * @return THe newly crated counter.
     */
    public static Counter newCounter(final String name) {
        return INSTANCE.privateNewCounter(name);
    }

    /**
     * Get a collection of counters.
     *
     * @return All the known counters.
     */
    public static Collection<Counter> getCounters() {
        return INSTANCE.privateGetCounters();
    }

    /**
     * Dump the counters to stdout.
     */
    public static void dumpCounters() {
        INSTANCE.privatedumpCounters();
    }

    /**
     * A map mapping strings to countes.
     */
    private final Map<String, Counter> counters =
 new TreeMap<>();

    /**
     * Since this is an utility class we can't have public constructor.
     */
    private Counters() {
    }

    /**
     * Dump the counters to stdout.
     */
    public void privatedumpCounters() {
        for (final Entry<String, Counter> entry : counters.entrySet()) {
        }
    }

    /**
     * Generate a new counter and put it into the counters map. throws
     * IllegalStateException if the name is already registred.
     *
     * @param name The name fo the counter.
     * @return a counter.
     */
    private Counter privateNewCounter(final String name) {
        synchronized (counters) {
            if (counters.containsKey(name)) {
                throw new IllegalStateException(
                        "Attempt to get two counters with name " + name);
            } else {
                final Counter result = new Counter(name);
                counters.put(name, result);
                return result;
            }
        }
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
