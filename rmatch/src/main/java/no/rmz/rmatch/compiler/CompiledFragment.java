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

package no.rmz.rmatch.compiler;

import static com.google.common.base.Preconditions.checkNotNull;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * Represent a compiled fragment of a regexp.   In general there will be
 * an arrival node and an ending node.  There may or may not be some nodes
 * i between (usually there are) but these are typically generated by
 * a compiler, and then glued to the arrival and  ending nodes through
 * epsilon edges.
 */
public final class CompiledFragment {

    /**
     * The regexp for which this is acompilation fragment.
     */
    private final Regexp r;

    /**
     * The entry-point for this fragment's NDFA.
     */
    private final NDFANode arrivalNode;

    /**
     * If succesfull traversal of the NDFA, this node will be reached.
     */
    private final NDFANode endingNode;

    /**
     * Generate a new compiled fragment where all the components are
     * parameterized in the constructor.
     *
     * @param r The regexp for which this is acompilation fragment.
     * @param arrivalNode The entry-point for this fragment's NDFA.
     * @param endingNode If succesfull traversal of the NDFA,
     *                   this node will be reached.
     */
    public CompiledFragment(
            final Regexp r,
            final NDFANode arrivalNode,
            final NDFANode endingNode) {
        this.r = checkNotNull(r);
        this.arrivalNode = checkNotNull(arrivalNode);
        this.endingNode = checkNotNull(endingNode);
    }

    /**
     * Create a new CompiledFragemen.  The arrival and ending nodes will
     * be new PaddingNDFANode instances.
     * @param r  the regex this fragment represents.
     */
    public CompiledFragment(final Regexp r) {
        this(r, new PaddingNDFANode(r), new PaddingNDFANode(r));
    }

    /**
     * Get the arrival node.
     * @return arrival node.
     */
    public NDFANode getArrivalNode() {
        return arrivalNode;
    }

    /**
     * Get the ending node.
     * @return endingNode.
     */
    public NDFANode getEndingNode() {
        return endingNode;
    }
}
