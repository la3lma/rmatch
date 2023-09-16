/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.*;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of a parsed regular expression.
 */
public final class RegexpImpl implements Regexp {
    /**
     * A nice little prime.
     */
    private static final int SMALLISH_PRIME = 5;
    /**
     * A nice two digit prime.
     */
    private static final int BIGISH_PRIME = 89;

    /**
     * The Regexp string represented by this Regexp.
     */
    private final String rexpString;
    /**
     * The set of actions associated with this regular expression. These actions
     * will be invoked when a match for the regular expression is detected.
     */
    private final Set<Action> actions = new HashSet<>();
    /**
     * The set of nodes that are currently involved in matching expressions for
     * this regular expression.
     */
    private final Set<Node> activeNodes = new HashSet<>();


    /**
     * The set of nodes that permit successful termination of matches for this
     * regular expression.
     */
    private final Set<Node> terminatingNodes = new HashSet<>();
    /**
     * A map from MatchSets to Domination heaps, so that we can get a domination
     * heap that is used to represent the matches within a single match set.
     * <p>
     * XXX It is a bit unclear if it is right to put this map in the Regexp
     * implementation. It may in fact be much saner to put it into the MatchSet,
     * however, the MatchSet doesn't use it itself.
     */
    private final Map<MatchSet, DominationHeap> heaps;
    /**
     * The starting node in the NDFA that represents this regular expression.
     */
    private NDFANode myNdfaNode;
    private Set<Character> nonStartingCharacters;

    /**
     * Make a new instance of Regexp representing a regular expression.
     *
     * @param rexpString a string representation of the regular expression.
     */
    public RegexpImpl(final String rexpString) {
        this.nonStartingCharacters = Collections.synchronizedSet(new TreeSet<Character>());
        this.heaps = new HashMap<>();
        checkNotNull(rexpString, "regexpString can't be null");
        this.rexpString = rexpString;
    }

    @Override
    public boolean isCompiled() {
        return (myNdfaNode != null);
    }

    @Override
    public void setMyNDFANode(final NDFANode myNode) {
        assert (!isCompiled());
        this.myNdfaNode = myNode;
    }

    @Override
    public NDFANode getMyNdfaNode() {
        return myNdfaNode;
    }

    @Override
    public boolean isActiveFor(final Node n) {
        checkNotNull(n);
        return activeNodes.contains(n);
    }

    @Override
    public void addActive(final Node n) {
        checkNotNull(n);
        activeNodes.add(n);
    }

    @Override
    public void addTerminalNode(final Node n) {
        checkNotNull(n);
        terminatingNodes.add(n);
    }

    @Override
    public boolean hasTerminalNdfaNode(final Node n) {
        checkNotNull(n);
        return terminatingNodes.contains(n);
    }

    @Override
    public String getRexpString() {
        return rexpString;
    }

    @Override
    public void add(final Action a) {
        checkNotNull(a);
        actions.add(a);
    }

    @Override
    public void registerNonStartingChar(final Character currentChar) {
        final Set<Character> cs = this.nonStartingCharacters;
        // TODO: COmmenting out just to see if it works. (It doesn't)
        if (cs.contains(currentChar)) {
             throw new IllegalStateException(
                    "The character " + currentChar + " is already registered as a non-starting character for this regexp, double registration is not allowed");
        }
        cs.add(currentChar);
    }

    @Override
    public boolean possibleStartingChar(Character currentChar) {
        if (this.nonStartingCharacters.contains(currentChar)) {
            return false;
        }

        if (this.myNdfaNode.cannotStartWith(currentChar)) {
            this.registerNonStartingChar(currentChar);
            return false;
        }

        return  true;
    }


    @Override
    public void remove(final Action a) {
        checkNotNull(a);
        actions.remove(a);
    }

    @Override
    public boolean hasAction(final Action a) {
        checkNotNull(a);
        return actions.contains(a);
    }

    @Override
    public boolean hasActions() {
        return !actions.isEmpty();
    }

    @Override
    public void performActions(
            final Buffer b,
            final int start,
            final int end) {
        for (final Action a : actions) {
            a.performMatch(b, start, end);
        }
    }

    @Override
    public DominationHeap getDominationHeap(final MatchSet ms) {
        DominationHeap dh = heaps.get(ms);

        if (dh == null) {
            dh = new DominationHeap();
            heaps.put(ms, dh);
        }
        return dh;
    }

    @Override
    public void abandonMatchSet(final MatchSet ms) {
        heaps.remove(ms);
    }

    @Override
    public void registerMatch(final Match m) {
        assert (m != null);
        getDominationHeap(m.getMatchSet()).addMatch(m);
        assert hasMatch(m);
    }

    @Override
    public boolean hasMatches() {
        return (heaps != null) && (!heaps.isEmpty());
    }

    /**
     * True iff m can never become dominating.
     * @param  m the node that may or may not dominate this strongy.
     * @return true iff this node is strongly dominated by m
     */
    @Override
    public boolean isStronglyDominated(final Match m) {
        return false; // XXX Possible future optimization
    }

    //  XXX Is "this" dominating argument?
    @Override
    public boolean isDominating(final Match m) {
        if (m.getRegexp() != this) {
            return false;  // Can only dominate if regexp is the same
        }
        final DominationHeap dm = getDominationHeap(m.getMatchSet());
        if (dm.isEmpty()) {
            // If we don't know anyting about the ms of the m, we can't dominate
            return false;
        }
        final Match mostDominant = dm.getFirstMatch();
        if (m == mostDominant) {
            // The most dominant object  dominates itself? Weird but correct?
            return true;
        }
        return (Match.COMPARE_BY_DOMINATION.compare(mostDominant, m) > 0);
    }

    @Override
    public void abandonMatch(final Match m, final Character currentChar) {
        checkNotNull(m);
        checkArgument(this == m.getRegexp());
        checkArgument(hasMatches());
        checkArgument(hasMatch(m));

        final MatchSet ms = m.getMatchSet();

        final DominationHeap dh = getDominationHeap(ms);
        // checkState(!dh.isEmpty());

        dh.remove(m);
        if (dh.isEmpty()) {
            heaps.remove(ms);
        }

        // Postcondition, just in case
        //
        // checkState(heaps == null
        //        || heaps.get(ms) == null
        //        || !getDominationHeap(ms).containsMatch(m));
    }

    @Override
    public void commitUndominated(final RunnableMatchesHolder runnableMatches) {
        if (heaps.isEmpty()) {
            return;
        }

        // From each DominationHeap, only pick the most dominant (hence
        // un-dominated) element.
        for (final DominationHeap dh : heaps.values()) {
            // Commit the current match if it's final
            final Match firstMatch = dh.getFirstMatch();
            if (firstMatch.isFinal()) {
                runnableMatches.add(firstMatch);
            }
        }
        // Then clear the heaps
        heaps.clear();
    }

    @Override
    public String toString() {
        return "RegexpImpl{string = '"
                + rexpString
                + "', actions = "
                + actions + "}";
    }

    @Override
    public int compareTo(final Regexp other) {
        checkNotNull(other);
        return this.getRexpString().compareTo(other.getRexpString());
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        } else if (o instanceof RegexpImpl) {
            return compareTo((Regexp) o) == 0;
        } else {
            return false;
        }
    }


    @Override
    public int hashCode() {
        final int hashFromString;
        if (this.rexpString != null) {
            hashFromString = this.rexpString.hashCode();
        } else {
            hashFromString = 0;
        }
        // XXX This looks stupid, probably is stupid.
        return SMALLISH_PRIME * BIGISH_PRIME + hashFromString;
    }

    @Override
    public boolean hasMatch(final Match m) {
        checkNotNull(m);
        final MatchSet ms = m.getMatchSet();
        return (heaps != null) && (heaps.get(ms) != null);
    }
}
