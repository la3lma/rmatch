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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.atomic.*;

/**
 * A counter that will give integer values starting from zero.
 */
public final class Counter {

    /**
     * True iff counter can be decremented. By default it can.
     */
    private boolean canBeDecremented = true;
    /**
     * Name of the counter. Lives in a global address space so all counter names
     * has to be unique.
     */
    private final String name;
    /**
     * The current value of the counter.
     */
    private AtomicLong value = new AtomicLong(0);
    /**
     * A monitor used to regulate access to the counter.
     */
    private final Object monitor = new Object();

    /**
     * Create a new counter. The name needs to be unique. By default counters
     * can be both incremented and decremented.
     *
     * @param name The name of the counter.
     */
    public Counter(final String name) {
        this.name = checkNotNull(name, "Counter name can't be null").trim();
        checkArgument(name.length() != 0, "Counter name can't be empty string");
    }

    /**
     * Increment the counter by one and return the new value.
     *
     * @return the new value.
     */
    public long inc() {
        return value.addAndGet(1);
    }

    /**
     * Decrement the counter by one and return the new value.
     *
     * @return the new value.
     */
    public long dec() {
        // XXX This use of the monitor is bogus given the way we
        //     have implemented inc without synchronization(which we do
        //     to avoid lock contention).
        synchronized (monitor) {
            if (!canBeDecremented) {
                throw new IllegalStateException(
                        "Can't decrement a counter that can't be decremented");
            }
            return value.decrementAndGet();
        }
    }

    /**
     * Disable decrementation for the counter.
     */
    public void setCannotBeDecremented() {
        synchronized (monitor) {
            canBeDecremented = false;
        }
    }

    @Override
    public String toString() {
        synchronized (monitor) {
            return "#'" + name + "'=" + value.get();
        }
    }
}
