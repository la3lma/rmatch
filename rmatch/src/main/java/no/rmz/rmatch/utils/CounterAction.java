/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.utils;

import no.rmz.rmatch.utils.Counters;
import no.rmz.rmatch.utils.Counter;
import java.util.logging.Logger;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.utils.Counter;
import no.rmz.rmatch.utils.Counters;

/**
 * Simple utility action used to count the number of something.
 */
public final class CounterAction implements Action {

    /**
     * Our dear old Log.
     */
    private static final Logger LOG =
            Logger.getLogger(CounterAction.class.getName());
    /**
     * How long to wait between reports.
     */
    private static final int
            DEFAULT_TICK_INTERVAL_FOR_IN_ACTIONS_BETWEEN_REPORTS = 4000;
    /**
     * Should the reports be verbose?
     */
    private boolean verbose = true;
    /**
     * Monitor used to synchronize access.
     */
    private final Object monitor = new Object();
    /**
     * The value of the counter.
     */
    private int counter = 0;
    /**
     * The tick interval we'll actually use.
     */
    private int tickInterval =
            DEFAULT_TICK_INTERVAL_FOR_IN_ACTIONS_BETWEEN_REPORTS;
    /**
     * Initialize the timestamp for the last tick to be the time of class
     * initialization.
     */
    private long lastTick = System.currentTimeMillis();

    @Override
    public void performMatch(final Buffer b, final int start, final int end) {
        synchronized (monitor) {
            counter += 1;


            if (verbose && (counter % tickInterval) == 0) {

                // Collecting a report from the known counters
                final StringBuilder sb = new StringBuilder("");
                for (final Counter c : Counters.getCounters()) {
                    sb.append("  ").append(c.toString()).append(", ");
                }

                // Making a report for the current counter,
                // plus all the counters.
                long now = System.currentTimeMillis();
                long duration = now - lastTick;
                double speed = (double) duration / (double) tickInterval;


                LOG.info("Match counter == " + counter
                        + ", duration = " + duration
                        + ", speed = " + speed + " millis/tick"
                        + ", start/end = " + start + "/" + end
                        + ", match string = '" + b.getString(start, end + 1)
                        + "' " + sb.toString());
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
