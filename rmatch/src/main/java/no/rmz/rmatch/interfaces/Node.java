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

/** A common node contract shared by rmatch's deterministic and nondeterministic automata. */
public interface Node {

  /**
   * Return whether a candidate for the supplied expression may continue at this node.
   *
   * @param r compiled regular-expression state
   * @return {@code true} if the node is active for {@code r}
   */
  boolean isActiveFor(final Regexp r);

  /**
   * Return whether this node is a legal terminal state for the supplied expression.
   *
   * @param r compiled regular-expression state
   * @return {@code true} if this node can complete a match for {@code r}
   */
  boolean isTerminalFor(final Regexp r);
}
