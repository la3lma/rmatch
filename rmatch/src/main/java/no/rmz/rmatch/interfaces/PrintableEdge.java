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
package no.rmz.rmatch.interfaces;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An edge used for printing. The label is a descriptive label describing what type of input is
 * necessary to traverse the edge. Typically it will be the regular expression that triggers
 * traversal, e.g. "[abc]", or "b" or something. It will only be an expression representing a single
 * character though.
 *
 * @param label A printable label, to be used by programs such as graphwiz to produce nice readable
 *     representations of the NDFAs used by the program.
 * @param destination The target of the edge.
 */
public record PrintableEdge(String label, NDFANode destination) {

  /**
   * A new printable edge, not used for matching but only for pretty printing of the DNFA Graph.
   *
   * @param label A printable label, or null for epsilon edges.
   * @param destination The target for the edge.
   */
  public PrintableEdge(final String label, final NDFANode destination) {
    this.label = label;
    this.destination = checkNotNull(destination);
  }

  /**
   * Get the target (destination) of the edge.
   *
   * @return The target.
   */
  @Override
  public NDFANode destination() {
    return destination;
  }

  /**
   * Get a descriptive label for the edge, typically a string like "a" or "[foo]" or some other
   * regular expression representing a single character.
   *
   * @return a nice descriptive string.
   */
  @Override
  public String label() {
    return label;
  }
}
