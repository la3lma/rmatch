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
import no.rmz.rmatch.utils.Counter;
import no.rmz.rmatch.utils.Counters;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

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
    /**
     * The set of matches being pursued through this MatchSetImpl.
     */
    private final Set<Match> matches;
    /**
     * The current determinstic node that is used when pushing the matches
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
     * @param startNode The deterministic start node to start with.
     */
    public MatchSetImpl(
            final int startIndex,
            final DFANode startNode,
            final Character peekedCharacter,
            final Collection<Regexp> regexpsKnownToStartWithPeekedCharacter,
            final Collection<Regexp> regexpsThatMayStartWithPeekedCharacter) {
        this.matches = new ConcurrentSkipListSet<>(Match.COMPARE_BY_OBJECT_ID);
        checkNotNull(startNode, "Startnode can't be null");
        checkArgument(startIndex >= 0, "Start index can't be negative");
        this.currentNode = startNode;
        this.start = startIndex;
        this.id = MY_COUNTER.inc();

        // XXX This lines represents the most egregious
        //     bug in the whole regexp package, since it
        //     incurs a cost in both runtime and used memory
        //     directly proportional to the number of
        //     expressions (m) the matcher matches for.  For a
        //     text that is l characters long, this  in turns
        //     adds a factor O(l*m) to the resource use of the
        //     algorithm.  Clearly not logarithmic in the number
        //     of expressions, and thus a showstopper.

        // XXX: Why are we not using regexp storage rather than current node
        //      as a source of regexps.  Because if we did, then we could interrogate
        //      using the char to find only the regexps that could possibly start.

        for (final Regexp r: regexpsKnownToStartWithPeekedCharacter) {
            matches.add(startNode.newMatch(this, r));
        }

        for (final Regexp r : regexpsThatMayStartWithPeekedCharacter) {
            // TODO: This is a hotspot.  Can we use a heuristic that can
            //       eliminate at least some of the new matches to be added?
            //       If so, that will give a noticeable improvement in speed.  If we can
            //       just halve the time spent we're in the business of beating
            //       the java regex matcher. Example: Is the next character we will see
            //       one that is compatible with adding a particular regex?  This
            //       can maybe be stored in a table so that it's easily cached
            //       and thus properly inner-loopy optimizable.
            //       For some regexps we know which ones can start on a char,
            //       and for those, maybe only run through exactly those and no others?
            if (!r.excludedAsStartCharacter(peekedCharacter)) {
                matches.add(startNode.newMatch(this, r));
           }
        }

        // This was necessary to nail the bug caused by the natural
        // comparison for matches not being by id. Don't want to
        // see that ever again, so I'm keeping the assertion.
        // TODO: This no longer is true due to the optimizations being introduced above.
        // assert (matches.size() == currentNode.getRegexps().size());
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
     * @param currentChar The currenc character.
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
            return;
        }

        // Check if there are any regexps for which matches must fail
        // for this node, and fail them.
        if (currentNode.failsSomeRegexps()) {
            for (final Match m : matches) {
                if (currentNode.isFailingFor(m.getRegexp())) {
                    m.abandon(currentChar);
                    matches.remove(m);
                }
            }
        }

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
            if (!isActive) {

                // Ok, we can't continue this match, perhaps it's already
                // final, and in that case we should commit what we've got
                // before abandoning it.
                if (m.isFinal()) {
                    commitMatch(m, runnableMatches);
                }

                if (!m.isAbandoned()) {
                    m.abandon(currentChar);
                }

                removeMatch(m);
            } else {

                // Mmkay, this is an active match and we have somewhere
                // to progress to, so we're advancing the end position of
                // the match by one.
                m.setEnd(currentPos);

                final boolean isFinal = currentNode.isTerminalFor(regexp);
                // If we're also in a final position for this match, note that
                // fact so that we can trigger actions for this match.
                m.setFinal(isFinal);
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
            // We can't commit what isn't final or is still active
            if (!m.isFinal() || m.isActive()) {
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
