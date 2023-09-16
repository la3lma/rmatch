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

import java.util.Collection;
import java.util.SortedSet;

/**
 * An interface that must be implemented by representations of nondeterminstic
 * finite automaton-nodes. (NDFAs).
 */
public interface NDFANode extends Node, Comparable<NDFANode> {

    /**
     * Epsilon edges are edges that connect nodes without the need to follow
     * any input. This method adds an epsilon edge from the present node
     * to some other node.
     * @param n The node that will be the gtarget of the epsilon edge.
     */
    void addEpsilonEdge(final NDFANode n);

    /**
     * Remove a node that is reachable from this node through an epsilon
     * (no-input) edge.
     *
     * @param n the node to remove.
     */
    void removeEpsilonReachableNode(final NDFANode n);

    /**
     * Get all the nodes that are reachable by epsilon edges.
     * @return All the nodes reachable by epsilon edges.
     */
    SortedSet<NDFANode> getEpsilons();



    /**
     * Give n that the nxt character is ch, what is the next
     * NDFA node that can be reached.  In the particular brand of nondeterminsm
     * implemented by this interface, there will for each outgoingcharacter
     * at most one node that is dreictly reachable through that character.
     * This is very much the common case  for regular expressions, but in the
     * not so uncommon special case where one wants multiple nodes to be
     * reached by the same character, this must be implemente by having
     * an intermeiate noe that is directly reached, and that node then has
     * to have epsilon nodes that reaches the other nodes that are to be
     * reached through the character.
     * @param ch  Get the node reachable through character ch.
     * @return the character to reach through (or something like that).
     */
    NDFANode getNextNDFA(final Character ch);

    /**
     * While getNextNDFA will give the single node that is reachable through
     * a character,  in general there will be more noes that are reached
     * due to the possib
     * e presence of epsilon nodes  going out of the target node found
     * by getNestNDFA.   The getNextSet method will  find the transitive,
     * reflexive
     * closure of the epsilon-reachable nodes going out of the node found by
     * getNextNDFA
     * the character ch.
     * @param ch The character we're lokking through
     * @return The set of nodes reachable thrugh the character ch.
     */
    SortedSet<NDFANode> getNextSet(final Character ch);

    /**
     * The regular expression this node is representing.
     * @return The Regexp this node is part of the representatio of.
     */
    Regexp getRegexp();

    @Override
    boolean isActiveFor(final Regexp rexp);

    /**
     * A collection of edges that can be used when printing a graph. Is not
     * necessarily when actually traversing the graph. Also, an implementation
     * may choose to return null for this method, for efficiency reasons or
     * other reasons.
     *
     * @return A collection of printable edges or null.
     */
    Collection<PrintableEdge> getEdgesToPrint();

    /**
     * If true, then the present node represents a valid termination of a match.
     * This means that a match that is in progress can be returned and executed,
     * because it is a legal match, but not necessarily that it will since there
     * may be other overlapping matches that should be run instead (determined
     * through the "domination protocol").
     *
     * @return iff the node is terminal.
     */
    boolean isTerminal();

    // XXX isTerminalFor and isTerminal is obviously overlapping in
    //     functionality.  This must be cleaned up.  The most obvious
    //     fix is to remove the isTerminalFor method and replace it
    //     with something else.
    @Override
    boolean isTerminalFor(final Regexp rexp);

    /**
     * If the NDFA exececution ever reaches a node for which isFailing is true,
     * the matcher must abandon any matches for the Regexp that which the
     * failing node represents.
     * <p>
     * A typical usecase for this type of node is for inverted matches:
     * "[^abc]".
     *
     * @return True iff this node dictates failing for matches associated with
     * this NDFANode's Regexp.
     */
    boolean isFailing();
}
