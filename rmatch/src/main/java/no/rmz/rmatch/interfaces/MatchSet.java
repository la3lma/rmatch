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

package no.rmz.rmatch.interfaces;

import java.util.Comparator;
import java.util.Set;
import no.rmz.rmatch.impls.RunnableMatchesHolder;

/**
 * A set of matches that is being processed by a matcher. The set of matches all
 * start at the same position in the input but may end at different positions.
 */
public interface MatchSet {


    /**
     * XXX Not really sure what this method does.  Review and report back.
     * @param target the recipient of matches that are committed.
     */
    void finalCommit(final RunnableMatchesHolder target);

    /**
     * Get all of the matches that are currently associated with the MatchSet.
     *
     * @return a set of Match instances.
     */
    Set<Match> getMatches();

    /**
     * The start position for all the matches in the input.
     *
     * @return a start position.
     */
    int getStart();

    /**
     * True if there are any matches in this MatcSet.
     * @return do we have any matches?
     */
    boolean hasMatches();

    /**
     * An identifier that uniquely identifies the match set.
     *
     * @return an id.
     */
    long getId();

    /**
     * Will progress the match-set one charater ahead. All of the matches that
     * can be contined will be continued, and those that can't will be aborted.
     * The matches that can be correctly terminated will be added to the set of
     * runnable matches through the runnableMatches instance, but only those
     * matches that is the "dominant" one will eventually run, the others will
     * be discarded silently.
     *
     * @param ns A node storage instance used to get new DFA nodes.
     * @param currentChar The current char.
     * @param currentPos The current position.
     * @param runnableMatches The set of runnable matches.
     */
    void progress(
            final NodeStorage ns,
            final Character currentChar,
            final int currentPos,
            final RunnableMatchesHolder runnableMatches);

    /**
     * Remove a match from the MatchSet instance.
     *
     * @param m a match.
     */
    void removeMatch(final Match m);
    /**
     * A comparator for match sets. Match sets are equal iff they have the same
     * identifier.
     */
    Comparator<MatchSet> COMPARE_BY_ID =
 (final MatchSet t, final MatchSet t1) -> {
                final long l1 = t.getId();
                final long l2 = t1.getId();

                if (l1 < l2) {
                    return -1;
                } else if (l2 < l1) {
                    return 1;
                } else {
                    return 0;
                }
            };
}
