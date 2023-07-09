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

import no.rmz.rmatch.impls.DominationHeap;

/**
 * A representation of a regular expression. Regexps are comparable to each
 * other, but essentially that is just comparison of the regexp strings.
 */
public interface Regexp extends Comparable<Regexp> {


    /**
     * Remove all references that the regexp has to the match m.
     *
     * @param m the match to remove references to.
     */
    void abandonMatch(final Match m, final Character currentChar);

    /**
     * Add an action to a regexp.
     *
     * @param a the action.
     */
    void add(final Action a);

    /**
     * Add a node and treat it as active.
     *
     * @param n the node to check for.
     */
    // XXX This should be DFA nodes. It never happens for NDFAs.
    void addActive(final Node n);

    /**
     * Allow a node to be treated as terminal.
     *
     * @param n a node
     */
    void addTerminalNode(final Node n);  // Can be both N and D FA.

    /**
     * Add all the matches that are not dominated to the set of runnable
     * matches.
     *
     * @param runnableMatches A holder of matches.
     */
    void commitUndominated(final RunnableMatchesHolder runnableMatches);

    /**
     * Get a domination heap for a MatchSet.
     *
     * @param ms the MatchSet
     * @return the corresponding DominationHeap.
     */
    DominationHeap getDominationHeap(final MatchSet ms);

    /**
     * Remove all references to a particular MatchSet, and the matches within
     * it.
     *
     * @param ms a MatchSet instance..
     */
    void abandonMatchSet(final MatchSet ms);

    /**
     * Get the NDFANode representing the start state for the NDFA that will
     * match this regular expression.
     *
     * @return the node representing this regexp.
     */
    NDFANode getMyNdfaNode();

    /**
     * A string representing the same regexp as the Regexp instance.
     *
     * @return the regexp string for this regular expression.
     */
    String getRexpString();

    /**
     * True iff the the regexp has actions.
     *
     * @param a an action
     * @return true if the action is associated with the regexp.
     */
    // XXX Only for testing, should be moved to implementation class.
    boolean hasAction(final Action a);

    /**
     * True iff the regexp has any actions (if it doesn't it can be ignored
     * since it can have no observable impact).
     *
     * @return true iff the regexp has any actions associated with it.
     */
    boolean hasActions();

    /**
     * True iff there are matches associated with this regexp.
     *
     * @return True iff there are matches associated with the regexp.
     */
    boolean hasMatches(); // XXX  Bogus?

    /**
     * True if a node is active for this regexp.
     *
     * @param n a node
     * @return true iff ithe node is active for the Regexp.
     */
    boolean isActiveFor(final Node n);

    /**
     * True iff the regexp has been compiled into an NDFA.
     *
     * @return true iff the regex is compiled to an ndfa.
     */
    boolean isCompiled();

    /**
     * True iff a match is dominating over all other matches for this regexp.
     *
     * @param m the match
     * @return is it dominating?
     */
    boolean isDominating(final Match m);

    /**
     * True iff the the node is terminal for this regexp.
     *
     * @param n the node.
     * @return true iff n is terminal for this regexp.
     */
    boolean hasTerminalNdfaNode(final Node n);

    /**
     * True iff the match is strongly dominated XXX Whatever that is!!
     *
     * @param m a match
     * @return true iff strongly dominated.
     */
    boolean isStronglyDominated(Match m);

    /**
     * Perform all actions on a buffer with a start a end location.
     *
     * @param b     the buffer
     * @param start the start location
     * @param end   the end location
     */
    void performActions(final Buffer b, final int start, final int end);

    /**
     * Register a match with this regexp.
     *
     * @param m a match that is registred with this regexp.
     */
    void registerMatch(final Match m);

    /**
     * Remove an action from this regexp.
     *
     * @param a removing an action from this regexp.
     */
    void remove(final Action a);

    /**
     * Set the compilation result to be an NDFA Node representing this regexp.
     *
     * @param myNode The NDFA node representing the entry point for the NDFA
     *               representing the regexp.
     */
    void setMyNDFANode(final NDFANode myNode);

    /**
     * True if the regexp is associated with a particular match. For testing use
     * only (XXX So it should be removed to the implementation class and used
     * from there only).
     *
     * @param m a match
     * @return true iff the match is associated with this regexp.
     */
    boolean hasMatch(final Match m);

    void registerNonStartingChar(Character currentChar);

    boolean possibleStartingChar(Character currentChar);
}
