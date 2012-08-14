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

import java.util.Collection;
import java.util.SortedSet;

/**
 * Implements a "subset construction mechanism". When matching a regular
 * expression, the expression is first compiled into a nondeterministic finite
 * automaton. That automaton is then opportunistically (incrementally, whatever)
 * compiled into a deterministic finite automaton. Now, the number of
 * determinstic nodes is O(2^N) where N is the number of nondeterminstic nodes,
 * so making them all is, for large regular expressions, usually not an option.
 * That is why we are opportunistic and cacheing etc.
 *
 * The NodeStorage is the interface used to abstract this mechanism away.
 */
public interface NodeStorage {

    /**
     * Add a new NDFANode to the startnode associated with the NodeStoarge.
     *
     * @param n a node to add.
     */
    void addToStartnode(final NDFANode n);

    /**
     * Get the determinstic node that represents the beginning of all matches
     * starting from the startnode that begins with the character ch.
     *
     * @param ch an input character.
     * @return a relevant DFANode, or null if no node could be found.
     */
    DFANode getNext(final Character ch);

    /**
     * Given a set of NDFANodes, return a DFANode representing that set of
     * NDFANOdes.
     *
     * @param ndfaset A set of nondeterminstic nodes we want to represent with a
     * single deterministic node.
     * @return A new deterministic node representing the input.
     */
    DFANode getDFANode(final SortedSet<NDFANode> ndfaset);

    /**
     * Get a snapshot of the currently stored NDFANodes.
     *
     * @return All the NDFANodes know to the NodeStorage.
     */
    Collection<NDFANode> getNDFANodes();

    /**
     * Get a snapshot of the currently stored DFAodes.
     *
     * @return All the NDFANodes know to the NodeStorage.
     */
    Collection<DFANode> getDFANodes();
}
