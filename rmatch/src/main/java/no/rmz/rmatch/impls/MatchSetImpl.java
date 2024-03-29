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
import no.rmz.rmatch.utils.Counter;
import no.rmz.rmatch.utils.Counters;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A an implementation of the MatchSet interface. A MatchSet keeps a set of
 * matches which starts from the same location in the input. The MatchSet will
 * initially contain several matches. As the matching process progresses fewer
 * and fewer matches will remain, and eventually they will all be removed either
 * when firing an action, or just removed since it is discovered that the match
 * can not be brought to be final and then executed.
 */
public final class MatchSetImpl implements MatchSet {
    /**
     * A counter for MatchSetImpls.
     */
    private static final Counter MY_COUNTER =
            Counters.newCounter("MatchSetImpl");

    /**
     * Commit this match relative to a bunch of other matches.
     * <p>
     * Now committing simply means adding this match to a collection of matches
     * given as parameters.
     * <p>
     * However, the current match is only added to the collection of runnable
     * matches if it's dominating the regular expression it's representing
     * <p>
     * If the current match is dominating its regular expression, then add it
     * to the set of runnable matches given as parameter.
     * <p>
     * This method is public only to facilitate testing. It's not part of any
     * interface and shouldn't be used directly anywhere.
     *
     * @param m the match to commit.
     * @param runnableMatches a collector of runnable matches
     */
    public static void commitMatch(
            final Match m,
            final RunnableMatchesHolder runnableMatches) {
        assert (!m.isActive());
        assert (m.isFinal());

        final boolean isDominating =
                m.getRegexp().isDominating(m);
        final boolean isStronglyDominating =
                m.getRegexp().isStronglyDominated(m);

        if (isDominating && !isStronglyDominating) {
            runnableMatches.add(m);
            m.getMatchSet().removeMatch(m);
        }
    }

    @Override
    public int hashCode(){
        return Long.hashCode(getId());
    }

    @Override
    public boolean  equals(final Object o){
        if (o instanceof MatchSet) {
            final MatchSet ms = (MatchSet) o;
            return ms.getId() == getId();
        } else {
            return false;
        }
    }

    /**
     * The set of matches being pursued through this MatchSetImpl.
     */
    private final Set<Match> matches;
    /**
     * The current deterministic node that is used when pushing the matches
     * further.
     */
    private DFANode currentNode;
    /**
     * The start position of all the matches associated with this MatchSetImpl.
     */
    private final int start;
    /**
     * An identifier uniquely identifying this MatchSetImpl among other
     * MatchSetImpl instances.
     */
    private final long id;

    /**
     * Create a new MatchSetImpl.
     *
     * @param startIndex The start position in the input.
     * @param newCurrentNode The deterministic start node to start with.
     */
    public MatchSetImpl(
            final int startIndex,
            final DFANode newCurrentNode) {
        this.matches = ConcurrentHashMap.newKeySet();
        checkNotNull(newCurrentNode, "newCurrentNode can't be null");
        checkArgument(startIndex >= 0, "Start index can't be negative");
        this.currentNode = newCurrentNode;
        start = startIndex;
        id = MY_COUNTER.inc();

        // XXX This lines represents the most egregious
        //     bug in the whole regexp package, since it
        //     incurs a cost in both runtime and used memory
        //     directly proportional to the number of
        //     expressions (m) the matcher matches for.  For a
        //     text that is l characters long, this  in turns
        //     adds a factor O(l*m) to the resource use of the
        //     algorithm.  Clearly not logarithmic in the number
        //     of expressions, and thus a showstopper.

        // Maybe introduce a function "canStartwith" for a regexp, and traverse
        // the regexps to find the ones that can start with the current char,
        // and only add those to the match set.
        // Also remember this for the next time, so that this calculation is
        // only carried once per character, for all the regexps?

        for (final Regexp r : this.currentNode.getRegexps()) {
            // TODO: We _must_ find some heuristic to optimize away most
            // invocations of the next line.
            matches.add(this.currentNode.newMatch(this, r));
        }
    }

    @Override
    public int getStart() {
        return start;
    }

    @Override
    public Set<Match> getMatches() {
        return Collections.unmodifiableSet(matches);
    }

    @Override
    public boolean hasMatches() {
        return !matches.isEmpty();
    }

    /**
     * Progress one, if any matches are inactivated they are removed from the
     * match set. If they have something to contribute they are committed to
     * runnableMatches too.
     *
     * @param ns A NodeStorage used to find new nodes.
     * @param currentChar The current character.
     * @param currentPos The current position.
     * @param runnableMatches a container for runnable matches. Matches will
     *        be put here if they can be run, possibly, pending domination
     *        stuff.
     */
    @Override
    public void progress(
            final NodeStorage ns,
            final Character currentChar,
            final int currentPos,
            final RunnableMatchesHolder runnableMatches) {

        checkNotNull(ns, "NodeStorage can't be null");
        checkNotNull(currentChar, "currentChar can't be null");
        checkArgument(currentPos >= 0, "currentPos must be non-negative");
        checkNotNull(runnableMatches, "runnableMatches can't be null");
        checkNotNull(currentNode,
                "currentNode can never be null when progressing");

        // If no matches are active, then there is nothing to do
        // so just return.
        if (!hasMatches()) {
            return;
        }

        // This nested if/for/if statement takes
        // care of all the circumstances

        currentNode = currentNode.getNext(currentChar, ns);

        if (currentNode == null) {
            terminateAssociatedMatches(currentChar, runnableMatches);
            return;
        }

        failMatchesThatCannotContinue(currentChar);

        progressMatches(currentChar, currentPos, runnableMatches);
    }

    private void terminateAssociatedMatches(Character currentChar, RunnableMatchesHolder runnableMatches) {
        // Found no nodes going out of the current node, so we have
        // to stop pursuing the matches we've already got.
        // This actually marks the MatchSetImpl instance for
        // destruction, but we won't do anything more about that fact
        // from within this loop.

        for (final Match m : matches) {
            m.setInactive();
            if (m.isFinal()) {
                commitMatch(m, runnableMatches);
                if (!m.isAbandoned()) {
                    m.abandon(currentChar);
                }
            }
            removeMatch(m);
        }
    }

    private void progressMatches(
            final Character currentChar,
            final int currentPos,
            final RunnableMatchesHolder runnableMatches) {
        // got a current  node, so we'll se what we can do to progress
        // the matches we've got.
        for (final Match m : matches) {

            // Get the regexp associated with the
            // match we're currently processing.
            final Regexp regexp = m.getRegexp();
            final boolean isActive = currentNode.isActiveFor(regexp);

            m.setActive(isActive);

            // If this node is active for the current regexp,
            // that means that we don't have to abandon
            if (isActive) {
                processActiveMatch(currentPos, m, regexp);
            } else {
                progressInactiveMatch(currentChar, runnableMatches, m);
            }
        }
    }

    /**
     *  This is an active match, and we have somewhere
     *  to progress to, so we're advancing the end position of
     *  the match by one.
     * @param currentPos The current position
     * @param m The match we are progressing
     * @param regexp The regexp associated with the match
     */
    private void processActiveMatch(
            final int currentPos,
            final Match m,
            final Regexp regexp) {

        m.setEnd(currentPos);

        final boolean isFinal = currentNode.isTerminalFor(regexp);
        // If we're also in a final position for this match, note that
        // fact so that we can trigger actions for this match.
        m.setFinal(isFinal);
    }

    /**
     * We can't continue this match, perhaps it's already
     * final, and in that case we should commit what we've got
     * before abandoning it.
     * @param currentChar The character we are currently processing
     * @param runnableMatches The matches we are currently running
     * @param m The current match
     */
    private void progressInactiveMatch(final Character currentChar, final RunnableMatchesHolder runnableMatches, final Match m) {

        if (m.isFinal()) {
            commitMatch(m, runnableMatches);
        }

        if (!m.isAbandoned()) {
            m.abandon(currentChar);
        }

        removeMatch(m);
    }

    private void failMatchesThatCannotContinue(Character currentChar) {
        // Check if there are any regexps for which matches must fail
        // for this node, and then fail them.
        if (currentNode.failsSomeRegexps()) {
            for (final Match m : matches) {
                if (currentNode.isFailingFor(m.getRegexp())) {
                    m.abandon(currentChar);
                    matches.remove(m);
                }
            }
        }
    }


    @Override
    public void removeMatch(final Match m) {
        checkNotNull(m);
        m.getRegexp().abandonMatchSet(this);
        matches.remove(m);
    }

    @Override
    public void finalCommit(final RunnableMatchesHolder runnableMatches) {
        checkNotNull(runnableMatches, "Target can't be null");

        final Set<Regexp> visitedRegexps = new HashSet<>();

        for (final Match m : matches) {
            if (m.notReadyForCommit()) {
                continue;
            }
            final Regexp r = m.getRegexp();
            if (!visitedRegexps.contains(r)) {
                visitedRegexps.add(r);
                r.commitUndominated(runnableMatches);
            }
            removeMatch(m);
        }
    }

    @Override
    public long getId() {
        return id;
    }
}
