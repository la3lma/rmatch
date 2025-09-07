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

import java.util.Collection;
import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * This is a node that has only epsilon edges going into it and only epsilon edges going out of it.
 * It is used to pad other automata.
 */
public final class PaddingNDFANode extends AbstractNDFANode {

  /**
   * Create a new padding node for a regular expression.
   *
   * @param r Our regexp.
   */
  public PaddingNDFANode(final Regexp r) {
    super(r, false);
  }

  @Override
  public NDFANode getNextNDFA(final Character ch) {
    return null;
  }

  @Override
  public Collection<PrintableEdge> getEdgesToPrint() {
    return getEpsilonEdgesToPrint();
  }
}
