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
package no.rmz.rmatch.impls;

// XXX Don't mix: Either use some sort of NDFA with
//     a charmap (and
//     epsilons), or use something else. Don't mix!!!

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import java.util.Collections;
import java.util.SortedSet;
import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.*;

/**
 * Shared NDFA start node for all expressions registered in one {@link NodeStorage}.
 *
 * <p>Each compiled expression is attached to this node by an epsilon edge. Starting a new match
 * therefore means asking this node which expression NDFAs can be entered for the current input
 * character.
 */
public final class StartNode extends AbstractNDFANode {
  /**
   * The regexp that is nominally associated with the start node. It is an empty expression that
   * does not match anything.
   */
  private static final Regexp START_NO_REGEXP = new RegexpImpl(""); // XX??

  /** The DFA that represents the StartNode instance. */
  @SuppressWarnings("unchecked")
  private final DFANodeImpl topDFA = new DFANodeImpl(Collections.EMPTY_SET);

  /** A monitor that is used to synchronize access to the StartNode instance. */
  private final Object topDfaMonitor = new Object();

  /** Create a start node. */
  public StartNode() {
    super(START_NO_REGEXP, false);
  }

  /**
   * Return the DFA start transition for a specific input character.
   *
   * @param ch input character
   * @param ns node storage used to create or reuse DFA nodes
   * @return DFA node reached from the global start state, or {@code null}
   */
  public DFANode getNextDFA(final Character ch, final NodeStorage ns) {

    if (this.topDFA.hasLinkFor(ch)) {
      return this.topDFA.getNext(ch, ns);
    }

    final SortedSet<NDFANode> nextSet = getNextSet(ch);

    final DFANode result;
    if (!nextSet.isEmpty()) {
      result = ns.getDFANode(nextSet);
      this.topDFA.addLink(ch, result);
    } else {
      result = null;
    }

    return result;
  }

  /**
   * Context-aware start transition used when zero-width assertions are present.
   *
   * @param ch input character
   * @param ns node storage
   * @param context positional context for assertion edges
   * @return DFA node reached from the global start state, or {@code null}
   */
  public DFANode getNextDFA(final Character ch, final NodeStorage ns, final MatchContext context) {
    if (context == MatchContext.NONE) {
      return getNextDFA(ch, ns);
    }
    final SortedSet<NDFANode> nextSet = getNextSet(ch, context);
    if (!nextSet.isEmpty()) {
      return ns.getDFANode(nextSet);
    }
    return null;
  }

  /**
   * Add an expression NDFA start node to the global start node.
   *
   * @param n expression start node to attach
   */
  public void add(final NDFANode n) {
    checkNotNull(n, "Can't add null NDFA node");
    addEpsilonEdge(n);
  }

  @Override
  public NDFANode getNextNDFA(final Character ch) {
    return null;
  }

  public DFANodeImpl asDfaNode() {
    return this.topDFA;
  }
}
