package no.rmz.rmatch.utils;

import no.rmz.rmatch.impls.MatchImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class AbandonStat {

    private static AbandonStat INSTANCE = null;

    private final Map<Integer, AtomicInteger> statistics =
            new HashMap<>();

    public static void record(final MatchImpl match) {
        AbandonStat.instance().recordAbandon(match);
    }

    private final Object sync = new Object();
    private void recordAbandon(final MatchImpl match) {
        final Integer key = match.getLength();
        synchronized (sync) {
            if (!statistics.containsKey(key)) {
                statistics.put(key, new AtomicInteger(0));
            }
            final AtomicInteger ai = statistics.get(key);
            ai.incrementAndGet();
        }
    }

    private static synchronized AbandonStat instance() {
        if (AbandonStat.INSTANCE == null) {
            AbandonStat.INSTANCE = new AbandonStat();
        }
        return AbandonStat.INSTANCE;
    }

    public static void dump() {
        AbandonStat.instance().dumpStats();
    }

    private void dumpStats() {
        for (final Integer key : this.statistics.keySet()) {
            final AtomicInteger ai = this.statistics.get(key);
            System.out.println("len = " + key + " ->  " + ai.get() + " abandoned");
        }
    }
}
