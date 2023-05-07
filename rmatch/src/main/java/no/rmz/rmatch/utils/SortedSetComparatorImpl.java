/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package no.rmz.rmatch.utils;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;

/**
 * Implements a comparator for sorted sets of T. All the elements in the set
 * must be comparable, and since the sets are sortable we can traverse the
 * elements in lexographical order. The first set to contain something the other
 * set doesn't contain is larger than the other.
 *
 * @param <T>
 */
public final class SortedSetComparatorImpl<T extends Comparable>
        implements Comparator<SortedSet<T>>, Serializable {

    @Override
    public int compare(final SortedSet<T> t, final SortedSet<T> t1) {
        final Iterator<T> ti  = t.iterator();
        final Iterator<T> t1i = t1.iterator();

        while (ti.hasNext() && t1i.hasNext()) {
            int r = ti.next().compareTo(t1i.next());
            if (r != 0) {
                return r;
            }
        }

        if (!ti.hasNext() && !t1i.hasNext()) {
            return 0;
        } else if (ti.hasNext() && !t1i.hasNext()) {
            return 1;
        } else if (!ti.hasNext() && t1i.hasNext()) {
            return -1;
        } else {
            throw new RuntimeException("This should never happen");
        }
    }
}
