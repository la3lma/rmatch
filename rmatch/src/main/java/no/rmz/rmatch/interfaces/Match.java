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

package no.rmz.rmatch.interfaces;

import java.util.Comparator;

/**
 * A Match represents a situation where a match has been found for some pattern.
 * There will typically be one or more consumers of the match, and these will
 * have to both extract the pattern that was matched for, and the content from
 * the Buffer being matched over that triggered the match.
 */
public interface Match {

    /**
     * Get the unique identifier for the match.
     *
     * @return an (long) integer uniquely identifying the match.
     */
    long getId();

    /**
     * Abandon this match. Remove all references to it from everything the match
     * knows points to it (the match set and the regexp).
     */
    void abandon(Character currentChar);

    /**
     * True iff the match has been abandoned.
     *
     * @return abandonment status.
     */
    boolean isAbandoned();

    /**
     * The MatchSet instance that contains this match.
     *
     * @return A MatchSet instance
     */
    MatchSet getMatchSet();

    /**
     * Return the Regexp instance that triggered this match.
     *
     * @return a Regexp instance.
     */
    Regexp getRegexp();

    /**
     * Get the index of the last element of the match.
     *
     * @return the index of the last element of the match.
     */
    int getEnd();

    /**
     * Get the index of the first index of the match.
     *
     * @return the index of the last character in the match.
     */
    int getStart();

    /**
     * True iff the match is actively being developed.
     *
     * @return acurrent activity state.
     */
    boolean isActive();

    /**
     * True iff the match represents a valid termination of the regular
     * expression is being matched.
     *
     * @return iff the current state represents a valid termination state.
     */
    boolean isFinal();

    /**
     * Set the current end position of the match.
     *
     * @param end the end position
     */
    void setEnd(int end);

    /**
     * Set the state if the match to be final.
     */
    void setIsFinal();

    /**
     * Set the state oft he match to be not final.
     */
    void setNotFinal();

    /**
     * Set the state of the match to be inactive.
     */
    void setInactive();

    /**
     * Set the activity state of the match to be whatever.
     *
     * @param activityState the new activity state.
     */
    void setActive(boolean activityState);

    /**
     * Set the finality state to be whatever.
     *
     * @param finalityState the new finality stae.
     */
    void setFinal(boolean finalityState);
    /**
     * A comparator that compares matches based on their unique identifier.
     */
    Comparator<Match> COMPARE_BY_OBJECT_ID =
            (t, t1) -> {
                // return Long.signum(t.getId() - t1.getId());
                final long l1 = t.getId();
                final long l2 = t1.getId();
                return Long.compare(l1, l2);
            };
    /**
     * A comparator that compares matches based on their domination status.
     */
    Comparator<Match> COMPARE_BY_DOMINATION = (final Match ths, final Match that) -> {
        if (ths == that) {
            return 0;
        } else if (ths.getRegexp() != that.getRegexp()) {
            return 0;
        } else if ((ths.getStart() == that.getStart())
                && (ths.getEnd() == that.getEnd())) {
            return 0;
        } else if ((ths.getStart() <= that.getStart())
                && (ths.getEnd() >= that.getEnd())) {
            return -1;
        } else if ((ths.getStart() >= that.getStart())
                && (ths.getEnd() <= that.getEnd())) {
            return 1;
        } else {
            return 0;
        }
    };

    default boolean isZeroLength() {
        return (this.getEnd() - this.getStart()) == 0;
    }
}
