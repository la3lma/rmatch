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

package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.*;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An implementation of a MatchEngine that can be used to match regular
 * expressions against input.
 */
public final class MatchEngineImpl implements MatchEngine {
    /**
     * Perform matches by triggering the relevant actions.
     *
     * @param b The buffer we are reading matches from.
     * @param matches The collection of matching we are performing.
     * @param bePermissive  If permissive, set all the matches to be
     *                      inactive and abandoned, otherwise
     *                      expect them to be inactive and abandoned
     *                      prior to invocation.
     */
    private static void performMatches(
            final Buffer b,
            final Collection<Match> matches,
            final Boolean bePermissive) {
        checkNotNull(matches);
        checkNotNull(b);
        for (final Match match : matches) {
            if (bePermissive) {
                match.setInactive();
            }
            performMatch(b, match);
            if (bePermissive) {
                match.abandon(null);
            }
        }
    }
    /**
     * Perform actions associated with a match.
     * @param b A buffer where the matched content is located.
     * @param m The match to be performed.
     */
    private static void performMatch(final Buffer b, final Match m) {
        checkNotNull(m);
        checkNotNull(b);
        if (m.isFinal()) {
            final int start = m.getStart();
            final int end = m.getEnd();
            final Regexp regexp = m.getRegexp();
            
            regexp.performActions(b, start, end);
        }
    }

    /**
     * The instance that is used to map sets of NDFA nodes to NDFA nodes, and if
     * necessarily create new DFA nodes.
     */
    private final NodeStorage ns;

    /**
     * Implements the MatchEngine interface, uses a particular NodeStorage
     * instance.
     *
     * @param ns non null NodeStorage instance.
     */
    public MatchEngineImpl(final NodeStorage ns) {
        this.ns = checkNotNull(ns, "NodeStorage can't be null");

    }

    /**
     * Progress the matcher one step.
     *
     * @param b The buffer we are reading characters from.
     * @param currentChar The current character.
     * @param currentPos The current position.
     * @param activeMatchSets The set of active match sets.
     */
    private void matcherProgress(
            final Buffer b,
            final Character currentChar,
            final int currentPos,
            final Set<MatchSet> activeMatchSets) {

        checkNotNull(currentChar, "currentChar can't be null");
        checkArgument(currentPos >= 0, "Pos in buf must be non-negative");

        // Progress all the already active matches and collect
        // the runnables.  The runnables may or may not be
        // active when they run, but they must be final.
        final RunnableMatchesHolder runnableMatches =
                new RunnableMatchesHolderImpl();

        if (!activeMatchSets.isEmpty()) {
            final Set<MatchSet> setsToRemove = new HashSet<>();
            for (final MatchSet ms : activeMatchSets) {
                ms.progress(ns, currentChar, currentPos, runnableMatches);
                if (!ms.hasMatches()) {
                    setsToRemove.add(ms);
                }
            }
            activeMatchSets.removeAll(setsToRemove);
        }

        // If the current input character opened up a possibility of new
        // matches, then by all means make a new match set to represent
        // that fact.
        final DFANode startOfNewMatches = ns.getNextFromStartNode(currentChar);
        if (startOfNewMatches != null) {
            final MatchSet ms;
            ms = new MatchSetImpl(currentPos, startOfNewMatches);
            if (ms.hasMatches()) {
                activeMatchSets.add(ms);
            }
        }

        // Then run through all the active match sets to see
        // if there are any
        // matches that should be commited.  When a matchSet is fresh out
        // of active matches it should be snuffed.

        for (final MatchSet ms : activeMatchSets) {

            // Collect runnable matches into the runnableMatches
            // instance.
            ms.finalCommit(runnableMatches);

            // If there are no matches in this match set, then we shouldn't
            // consider it any further.
            if (!ms.hasMatches()) {
                activeMatchSets.remove(ms);
            }
        }

        // Run through all the runnable matches and perform actions.
        // (Don't be permissive by default, hence "false").
        performMatches(b, runnableMatches.getMatches(), false);
    }

    @Override
    public void match(final Buffer b) {
        checkNotNull(b, "Buffer can't be null");

        final Set<MatchSet> activeMatchSets;
        activeMatchSets = Collections.synchronizedSet(
                new TreeSet<>(MatchSet.COMPARE_BY_ID));

        // Advance all match sets forward one character.
        while (b.hasNext()) {
            final Character nextChar = b.getNext();
            final int currentPos = b.getCurrentPos();
            matcherProgress(b, nextChar, currentPos, activeMatchSets);
        }

        // Handle the stragglers
        for (final MatchSet ms : activeMatchSets) {
            // Be permissive when handling stragglers
            performMatches(b, ms.getMatches(), true);
        }

        activeMatchSets.clear();
    }
}
