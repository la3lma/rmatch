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

/**
 * A generic node used both in deterministic and nondeterminstic automata.
 */
public interface Node {

    /**
     * True iff the node is relevant for a regexp. The meaning is that if we for
     * some reason arrive at this node and is working on a match for a particuar
     * regexp, then we can continue to work on that match iff the node is active
     * for that regexp.
     *
     * @param r A regular expression representation.
     * @return true iff the present node is relevant for the parameter regexp
     */
    boolean isActiveFor(final Regexp r);

    /**
     * True iff the present node is a legal termination node for the parameter
     * regexp.
     *
     * @param r A regular expression representation.
     * @return true iff the present node is a legl termination node for the
     * parameter regexp.
     */
    boolean isTerminalFor(final Regexp r);
}
