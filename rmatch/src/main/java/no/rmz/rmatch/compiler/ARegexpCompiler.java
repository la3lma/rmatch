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
import static no.rmz.rmatch.internal.Checks.checkState;

import java.util.ArrayDeque;
import java.util.Deque;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.ZeroWidthAssertion;

/**
 * A (partial) compiler that will produce NDFAs that represents regular expressions. The compiler
 * implements the AbstractRegexBuilder interface which will feed it with parsed elements of the
 * regexp.
 */
public final class ARegexpCompiler implements AbstractRegexBuilder {

  /**
   * A stack of alternatives builders: the bottom element builds the whole expression, and each open
   * group pushes a fresh builder for its subexpression. All parsed elements go to the innermost
   * (top) builder.
   */
  private final Deque<AlternativesBuilder> builders = new ArrayDeque<>();

  /** The top of the builder stack: the builder for the innermost open scope. */
  final AlternativesBuilder alternativesBuilder() {
    return builders.peek();
  }

  /** The last, and hence an epsilon-node going out of the last element of the resultFragments. */
  private final TerminalNode terminal;

  /** The regexp we're compiling. */
  private final Regexp regexp;

  /**
   * When building a character set, we use this builder instance to do it for us. A new instance for
   * each char set.
   */
  private CharSetBuilder charSetStringBuilder;

  /**
   * Create a new compiler for a particular regexp.
   *
   * @param regexp The Regexp we are compiling.
   */
  public ARegexpCompiler(final Regexp regexp) {
    this.regexp = checkNotNull(regexp);
    this.terminal = new TerminalNode(regexp);
    builders.push(new AlternativesBuilder(regexp));
  }

  /**
   * XXX Once this method has been called, no further content can be added. This is currently not
   * reflected in the implementations, so errors can be introduced!!! must be fixed asap.
   *
   * @return Returns an NDFANode that represent the compilation of the regexp.
   */
  public NDFANode getResult() {
    checkState(builders.size() == 1, "Unbalanced group: missing ')' or ')' without '('");
    final CompiledFragment result = alternativesBuilder().build();
    result.getEndingNode().addEpsilonEdge(terminal);
    return result.getArrivalNode();
  }

  @Override
  public void addString(final String str) {
    final CompiledFragment result = new CompiledFragment(regexp);
    final NDFANode arrival = result.getArrivalNode();

    // Produce a chain of nodes linked by the next character.
    // build the chain backwards from the last char in the string
    // back to the first chracter in the string.
    NDFANode nextNode = result.getEndingNode();
    for (int i = str.length() - 1; i >= 0; i--) {
      final Character myChar = str.charAt(i);
      nextNode = new CharNode(nextNode, myChar, regexp);
    }

    // Finally hook the  arrival node of the result up to
    // the first node in the chain representing the string.
    arrival.addEpsilonEdge(nextNode);

    // If there are no alternatives accumulated so far, or we've just
    // seen a newAlternative been set, start a new alternative,
    // otherwise we append the NDFA representing
    // the string to the end of the last node in the current set of
    // alternatives.
    alternativesBuilder().addLast(result);
  }

  @Override
  public void separateAlternatives() {
    alternativesBuilder().separateAlternatives();
  }

  @Override
  public void startCharSet() {
    charSetStringBuilder = new CharSetBuilder(regexp);
  }

  @Override
  public void endCharSet() {
    final CompiledFragment result = charSetStringBuilder.build();
    alternativesBuilder().addLast(result);
  }

  @Override
  public void invertCharSet() {
    charSetStringBuilder.invert();
  }

  @Override
  public void addToCharSet(final String cs) {
    charSetStringBuilder.addChars(cs);
  }

  @Override
  public void addRangeToCharSet(final char startOfRange, final char endOfRange) {
    charSetStringBuilder.addRange(startOfRange, endOfRange);
  }

  @Override
  public void addAnyChar() {
    final CompiledFragment fragment = new CompiledFragment(regexp);
    final NDFANode resultNode = new AnyCharNode(fragment.getEndingNode(), regexp);
    fragment.getArrivalNode().addEpsilonEdge(resultNode);
    alternativesBuilder().addLast(fragment);
  }

  @Override
  public void addBeginningOfLine() {
    addAssertion(ZeroWidthAssertion.LINE_START);
  }

  @Override
  public void addEndOfLine() {
    addAssertion(ZeroWidthAssertion.LINE_END);
  }

  private void addAssertion(final ZeroWidthAssertion assertion) {
    regexp.markUsesContextAssertions();
    final CompiledFragment fragment = new CompiledFragment(regexp);
    fragment.getArrivalNode().addAssertionEdge(assertion, fragment.getEndingNode());
    alternativesBuilder().addLast(fragment);
  }

  @Override
  public void addOptionalSingular() {
    final CompiledFragment last = alternativesBuilder().getLastFragment();
    last.getArrivalNode().addEpsilonEdge(last.getEndingNode());
  }

  @Override
  public void addOptionalZeroOrMulti() {
    final CompiledFragment last = alternativesBuilder().getLastFragment();
    last.getArrivalNode().addEpsilonEdge(last.getEndingNode());
    last.getEndingNode().addEpsilonEdge(last.getArrivalNode());
  }

  @Override
  public void addOptionalOnceOrMulti() {
    final CompiledFragment last = alternativesBuilder().getLastFragment();
    last.getEndingNode().addEpsilonEdge(last.getArrivalNode());
  }

  @Override
  public void startGroup() {
    builders.push(new AlternativesBuilder(regexp));
  }

  @Override
  public void endGroup() {
    checkState(builders.size() > 1, "')' without matching '('");
    final CompiledFragment group = builders.pop().build();
    // The finished group becomes a single quantifiable atom in the enclosing scope.
    alternativesBuilder().addLast(group);
  }
}
