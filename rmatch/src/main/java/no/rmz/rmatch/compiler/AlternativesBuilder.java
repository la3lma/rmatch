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

package no.rmz.rmatch.compiler;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import java.util.LinkedList;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * Compute a CompiledFragment that represents a set of alternatives, e.g.
 * "a|b|c".
 */
public final class AlternativesBuilder {

    /**
     * The Regexp instance for which this builder compiles a fragment.
     */
    private final Regexp regexp;
    /**
     * True iff the result has already been computed and returned.
     */
    private boolean hasBeenBuilt = false;
    /**
     * True iff we should treat the next input as a new alternative.
     */
    private boolean newAlternative = true;
    /**
     * This will be the result of the compilation. The actual result returned by
     * the getResult method is the arrival node of the first CompiledFeragment.
     */
    private final LinkedList<CompiledFragment> alternatives =
            new LinkedList<>();
    /**
     * A reference to the last fragment added, or null if we are parsing a new
     * alternative.
     */
    private CompiledFragment lastFragment;

    /**
     * Construct a new alternatives builder for a regexp.
     *
     * @param regexp the regexp that the alternatives will be a part of.
     */
    public AlternativesBuilder(final Regexp regexp) {
        this.regexp = checkNotNull(regexp);
    }

    /**
     * Build a compiled fragment based on the alternatives.
     *
     * @return a compilation result
     */
    public CompiledFragment build() {
        checkState(!hasBeenBuilt,
                "Attempt to build an alrady built AlternativesBuilder");
        wrapSequenceOfFragmentIntoFragment();
        final CompiledFragment result = new CompiledFragment(regexp);
        final NDFANode arrival = result.getArrivalNode();
        final NDFANode endNode = result.getEndingNode();
        for (final CompiledFragment f : alternatives) {
            arrival.addEpsilonEdge(f.getArrivalNode());
            f.getEndingNode().addEpsilonEdge(endNode);
        }
        hasBeenBuilt = true;
        return result;
    }

    /**
     * When starting a new alternative, the old sequence of patterns must be
     * wrapped into a CompiledFragment representing the alternative. This method
     * does that by performing a bit of surgery on the list.
     */
    private void wrapSequenceOfFragmentIntoFragment() {
        if (lastFragment != null) {
            final CompiledFragment alternative = new CompiledFragment(regexp);
            final CompiledFragment alternativeHead = alternatives.getLast();
            alternative.getArrivalNode()
                    .addEpsilonEdge(alternativeHead.getArrivalNode());
            lastFragment.getEndingNode()
                    .addEpsilonEdge(alternative.getEndingNode());
            alternatives.removeLast();
            alternatives.add(alternative);
        }
    }

    /**
     * Add a new fragment to the set of alternatives. Add it to the current
     * alternative being built, or create a new one if instructed to by having
     * the newAlternative flag set.
     * @param  fragment fragment to add after the last one.
     */
    public void addLast(final CompiledFragment fragment) {

        checkState(!hasBeenBuilt,
                "Attempt to add more to an already built AlternativesBuilder");
        if (newAlternative) {
            wrapSequenceOfFragmentIntoFragment();

            alternatives.add(fragment);
            newAlternative = false;
        } else {
            lastFragment
                    .getEndingNode()
                    .addEpsilonEdge(fragment.getArrivalNode());
        }
        lastFragment = fragment;
    }

    /**
     * Return the last compiled fragment that was added.
     *
     * @return A compiled fragment.
     */
    public CompiledFragment getLastFragment() {
        checkState(!hasBeenBuilt,
                "Attempt extract something from "
                + "an already built AlternativesBuilder");
        return lastFragment;
    }

    /**
     * When separating alternatives, this method is called to make sure that the
     * next time addLast is added, the next element is added to a new
     * alternative.
     */
    public void separateAlternatives() {
        checkState(!hasBeenBuilt,
                "Attempt to add more to an already built AlternativesBuilder");
        wrapSequenceOfFragmentIntoFragment();
        lastFragment = null;
        newAlternative = true;
    }
}
