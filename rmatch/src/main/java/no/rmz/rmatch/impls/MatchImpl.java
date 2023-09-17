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

import no.rmz.rmatch.interfaces.Match;
import no.rmz.rmatch.interfaces.MatchSet;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.utils.Counter;
import no.rmz.rmatch.utils.Counters;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A representation of a match implementing the Match interface.
 */
public final class MatchImpl implements Match {
    /**
     * A counter that is increased every time a new MatchImpl is generated. Used
     * for logging and benchmarking.
     */
    private static final Counter counter = Counters.newCounter("MatchImpl");

    /**
     * True iff the match is final, in the sense that its current state
     * represents a legal termination of the match.
     */
    private boolean isFinal;
    /**
     * True iff this match can expect to progress further.
     */
    private boolean isActive;
    /**
     * The end position of the match. The start position can be found indirectly
     * through the MatchSet ms.
     */
    private int end;
    /**
     * The Regexp for which this match is valued.
     */
    private final Regexp r;
    /**
     * The MatchSet of which this Match is a part..
     */
    private final MatchSet ms;
    /**
     * An unique ID for the set of matches.
     */
    private final long id;

    /**
     * Create a new Match implementation.
     *
     * @param ms The MatchSet with which this Match is associated.
     * @param r The Regexp with which this match is associated.
     * @param isFinal True iff the match is final from the get-go.
     */
    public MatchImpl(
            final MatchSet ms,
            final Regexp r,
            final boolean isFinal) {
        this.ms = checkNotNull(ms, "MatchSet can't be null");
        this.r = checkNotNull(r, "Regexp can't be null");
        this.isFinal = isFinal; // why?
        end = ms.getStart(); // XXX Bogus
        isActive = true;
        r.registerMatch(this);
        id = counter.inc();
    }

    /**
     * Implement hashing based on the unique ID.
     */
    @Override
    public int hashCode() {
        return (int) (this.id ^ (this.id >>> 32));
    }

    /**
     * Create a new Match implementation. Assumes that the match is not
     * initially final.
     *
     * @param ms The MatchSet with which this Match is associated.
     * @param r The Regexp with which this match is associated.
     */
    public MatchImpl(final MatchSet ms, final Regexp r) {
        this(ms, r, false); //  By default matches are not final
    }

    @Override
    public String toString() {
        return "[Match  regexpString = '"
                + r.getRexpString() + "' start = "
                + ms.getStart() + " end = "
                + end + " isFinal = "
                + isFinal + " isActive " + isActive + "]";
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void abandon(Character currentChar) {
        checkState(!isAbandoned());
        r.abandonMatch(this, currentChar);
        isActive = false;
    }

    @Override
    public boolean isAbandoned() {
        return !isActive && (!r.hasMatches() || !r.hasMatch(this));
    }

    @Override
    public MatchSet getMatchSet() {
        return ms;
    }

    @Override
    public void setIsFinal() {
        isFinal = true;
    }

    @Override
    public int getStart() {
        return ms.getStart();
    }

    @Override
    public int getEnd() {
        return end;
    }

    @Override
    public void setEnd(final int end) {
        this.end = end;
    }

    @Override
    public Regexp getRegexp() {
        return r;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public boolean isFinal() {
        return isFinal;
    }

    @Override
    public void setNotFinal() {
        isFinal = false;
    }

    @Override
    public void setInactive() {
        isActive = false;
    }

    @Override
    public void setActive(final boolean active) {
        this.isActive = active;
    }

    @Override
    public void setFinal(final boolean aFinal) {
        this.isFinal = aFinal;
    }
}
