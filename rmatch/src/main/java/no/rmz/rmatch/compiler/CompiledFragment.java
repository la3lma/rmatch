/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rmz.rmatch.compiler;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * Entry and exit pair for a partially compiled NDFA fragment.
 *
 * <p>The compiler builds larger expressions by connecting fragments with epsilon edges. The {@code
 * startNode} is where control enters the fragment; {@code endNode} is where later fragments can be
 * attached.
 */
final class CompiledFragment {

  /** The entry-point for this fragment's NDFA. */
  private final NDFANode arrivalNode;

  /** If successful traversal of the NDFA, this node will be reached. */
  private final NDFANode endingNode;

  /**
   * Generate a new compiled fragment where all the components are parameterized in the constructor.
   *
   * @param r The regexp for which this is a compilation fragment.
   * @param arrivalNode The entry-point for this fragment's NDFA.
   * @param endingNode If successful traversal of the NDFA, this node will be reached.
   */
  public CompiledFragment(final Regexp r, final NDFANode arrivalNode, final NDFANode endingNode) {
    checkNotNull(r);
    this.arrivalNode = checkNotNull(arrivalNode);
    this.endingNode = checkNotNull(endingNode);
  }

  /**
   * Create a new CompiledFragement. The arrival and ending nodes will be new PaddingNDFANode
   * instances.
   *
   * @param r the regex this fragment represents.
   */
  public CompiledFragment(final Regexp r) {
    this(r, new PaddingNDFANode(r), new PaddingNDFANode(r));
  }

  /**
   * Get the arrival node.
   *
   * @return arrival node.
   */
  public NDFANode getArrivalNode() {
    return arrivalNode;
  }

  /**
   * Get the ending node.
   *
   * @return endingNode.
   */
  public NDFANode getEndingNode() {
    return endingNode;
  }
}
