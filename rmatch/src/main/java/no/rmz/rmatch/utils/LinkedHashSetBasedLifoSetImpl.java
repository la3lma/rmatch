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

import java.util.*;

/**
 * A mixture between a set and a LIFO stack. Adding the same element more than
 * once will not insert it more than once.
 * @param <T>
 */
public final  class LinkedHashSetBasedLifoSetImpl<T> implements LifoSet<T> {

    // XXX I'm dubious about the synchronization model for this class.
    //     It looks bogus to me.
    
    /**
     * A set that holds all the member of the lifoset.
     */
    private final LinkedHashSet<T> members =
                    new LinkedHashSet<>();

    public  LinkedHashSetBasedLifoSetImpl() {
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
        synchronized(members) {
            return members.add(t);
        }
    }

    private T lastElement() {
        final Iterator<T> i = members.iterator();
        T last = null;
        for (last = i.next() ; i.hasNext(); last = i.next()){}
        return last;
    }

    /**
     * Return the last element that was added to the LIFOset, then remove it
     * from the LIFOset.
     *
     * @return the element that was last added to the LIFOset.
     */
    @Override
    public T pop() {
        synchronized(members) {
            final T last = lastElement();
            if (last == null) {
                throw new IllegalStateException(
                        "Attempt to remove something from an empty LifoSet");
            }

            members.remove(last);
            return last;
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
        members.addAll(elementSet);
    }

    /**
     * Return true iff the LifoSetImpl contains the element.
     *
     * @param element the element to check for the presence of.
     * @return true iff the parameter is present in the LifoSetImpl.
     */
    @Override
    public boolean contains(final T element) {
        return members.contains(element);
    }

    @Override
    public String toString() {
        synchronized (members) {
            return "LifoSet{" + "lifo=" + members + '}';
        }
    }
}
