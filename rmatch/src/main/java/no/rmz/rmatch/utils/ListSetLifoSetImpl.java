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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A mixture between a set and a LIFO stack. Adding the same element more than
 * once will not insert it more than once.
 * @param <T> is the class of the instances that the LifoSetImpl will contain.
 */
public final  class ListSetLifoSetImpl<T> implements LifoSet<T> {

    /**
     * A set that holds all the member of the lifoset.
     */
    private final Set<T> members = new HashSet<>();
    /**
     * A list that is used to represent the LIFO aspect of the LIFO set.
     */
    private final List<T> lifo = new ArrayList<>();

    public  ListSetLifoSetImpl() {
    }


    /**
     * True iff there are no members.
     *
     * @return true iff no content in members.
     */
    @Override
    public boolean isEmpty() {
        synchronized (members) {
            return members.isEmpty();
        }
    }

    /**
     * If the element being added was already present in the LIFOset, then it
     * isn't added again, otherwise it is added.
     *
     * @param t the element to be added
     * @return true iff the element was not already present and therefore was
     * added by the add method, otherwise (obviously) false.
     */
    @Override
    public boolean add(final T t) {
        synchronized (members) {
            if (!members.contains(t)) {
                members.add(t);
                lifo.add(t);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Return the last element that was added to the LIFOset, then remove it
     * from the LIFOset.
     *
     * @return the element that was last added to the LIFOset.
     */
    @Override
    public T pop() {
        synchronized (members) {
            if (isEmpty()) {
                throw new IllegalStateException(
                        "Attempt to remove something from an empty LifoSet");
            }
            final T result = lifo.remove(0);
            members.remove(result);
            return result;
        }
    }

    /**
     * Add a set of elements to the LifoSetImpl. The order in which they are added
     * is the same as they would be returned in by the default iterator for the
     * set.
     *
     * @param elementSet a set of elements to add.
     */
    @Override
    public void addAll(final Set<T> elementSet) {
        synchronized (members) {
            for (final T t : elementSet) {
                add(t);
            }
        }
    }

    /**
     * Return true iff the LifoSetImpl contains the element.
     *
     * @param element the element to check for the presence of.
     * @return true iff the parameter is present in the LifoSetImpl.
     */
    @Override
    public boolean contains(final T element) {
        synchronized (members) {
            return members.contains(element);
        }
    }

    @Override
    public String toString() {
        synchronized (members) {
            return "LifoSet{" + "lifo=" + lifo + '}';
        }
    }
}
