/*
 * Copyright 2013 Rmz.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.rmz.rmatch.utils;

import java.util.*;

public interface LifoSet<T> {

    /**
     * If the element being added was already present in the LIFOset, then it
     * isn't added again, otherwise it is added.
     *
     * @param t the element to be added
     * @return true iff the element was not already present and therefore was
     * added by the add method, otherwise (obviously) false.
     */
    boolean add(final T t);

    /**
     * Add a set of elements to the LifoSetImpl. The order in which they are added
     * is the same as they would be returned in by the default iterator for the
     * set.
     *
     * @param elementSet a set of elements to add.
     */
    void addAll(final Set<T> elementSet);

    /**
     * Return true iff the LifoSetImpl contains the element.
     *
     * @param element the element to check for the presence of.
     * @return true iff the parameter is present in the LifoSetImpl.
     */
    boolean contains(final T element);

    /**
     * True iff there are no members.
     *
     * @return true iff no content in members.
     */
    boolean isEmpty();

    /**
     * Return the last element that was added to the LIFOset, then remove it
     * from the LIFOset.
     *
     * @return the element that was last added to the LIFOset.
     */
    T pop();
}
