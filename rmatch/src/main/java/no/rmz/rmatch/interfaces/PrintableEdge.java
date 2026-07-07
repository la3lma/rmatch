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

import static no.rmz.rmatch.internal.Checks.checkNotNull;

/**
 * Diagnostic edge description used by graph-rendering tools.
 *
 * <p>A {@code PrintableEdge} is not consulted by the matcher. It is a lightweight description of a
 * node-to-node connection for tools that draw the compiled automata. The label is intended for
 * humans, for example {@code "a"}, {@code "[abc]"}, or {@code null} for an epsilon edge.
 *
 * @param label human-readable edge label, or {@code null} for an epsilon edge
 * @param destination destination node
 */
public record PrintableEdge(String label, NDFANode destination) {

  /**
   * Create a diagnostic edge description.
   *
   * @param label human-readable edge label, or {@code null} for an epsilon edge
   * @param destination destination node
   */
  public PrintableEdge(final String label, final NDFANode destination) {
    this.label = label;
    this.destination = checkNotNull(destination);
  }

  /**
   * Return the destination node.
   *
   * @return destination node
   */
  @Override
  public NDFANode destination() {
    return destination;
  }

  /**
   * Return the human-readable label for this edge.
   *
   * @return edge label, or {@code null} for an epsilon edge
   */
  @Override
  public String label() {
    return label;
  }
}
