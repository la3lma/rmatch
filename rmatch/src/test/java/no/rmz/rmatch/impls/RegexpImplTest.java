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

import static org.junit.jupiter.api.Assertions.*;

import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.utils.StringBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Testing the implementation of our Regexp interface. */
@ExtendWith(MockitoExtension.class)
public class RegexpImplTest {

  /** Test article, an instance of RegexpImpl. */
  private RegexpImpl re;

  /** A string that will represent the regexp that "re" represents. */
  private String reString;

  /** A Node that is added to the regexp to check various things. */
  private Node node;

  /**
   * An action. XXX This should have been mockito mocked or extracted into a separate helper class.
   */
  private Action a;

  /**
   * True iff the action fired. XXX This should have been mockito mocked or extracted into a
   * separate helper class.
   */
  private boolean actionFired;

  /** The buffer that was seen when firing the action. */
  private Buffer firedBuffer;

  /** The start position that was seen when firing the action. */
  private int firedStart;

  /** The end position that was seen when firing the action. */
  private int firedEnd;

  /** The buffer used to give input to the matcher, or in this case used when firing an action. */
  private Buffer b;

  /** The number of times the action has fired. */
  private int timesFired;

  /** Set up the test articles. */
  @BeforeEach
  public final void setUp() {
    reString = "Fnord";
    re = new RegexpImpl(reString);
    node =
        new Node() {
          @Override
          public boolean isActiveFor(final Regexp rexp) {
            return rexp.isActiveFor(this);
          }

          @Override
          public boolean isTerminalFor(final Regexp rexp) {
            return rexp.hasTerminalNdfaNode(this);
          }
        };
    actionFired = false;
    timesFired = 0;
    b = new StringBuffer("Fnord");
    a =
        (b, start, end) -> {
          timesFired += 1;
          firedBuffer = b;
          firedStart = start;
          firedEnd = end;
          actionFired = true;
        };
  }

  /** Check that the node is active for the regexp. */
  @Test
  public void testActive() {
    re.addActive(node);
    assertTrue(re.isActiveFor(node));
    assertTrue(node.isActiveFor(re));
  }

  /** Test that the node is terminal for the regexp. */
  @Test
  public void testTerminalFor() {
    re.addTerminalNode(node);
    assertTrue(re.hasTerminalNdfaNode(node));
    assertTrue(node.isTerminalFor(re));
  }

  /** Test that the regexp string in the re instance is what we would expect it to be. */
  @Test
  public void testRexpString() {
    assertEquals(re.getRexpString(), reString);
  }

  /** Test that the regexp has some actions after adding an action. */
  @Test
  public void testHasActions() {
    assertFalse(re.hasActions());
    re.add(a);
    assertTrue(re.hasActions());
  }

  /** Check that adding an action doesn't fail. */
  @Test
  public void testAddAction() {
    re.add(a);
    assertTrue(re.hasAction(a));
  }

  /** Checking that removing an action works. */
  @Test
  public void testRemoveAction() {
    testAddAction();
    re.remove(a);
    assertFalse(re.hasAction(a));
  }

  /** Mock up a match to use when testing the match registration mechanism. */
  // @Mock
  //  Match match;
  @Mock MatchSet matchSet;

  @Mock Regexp otherRegex;

  /** Test the match registration mechanism. */
  @Test
  public void testRegisterMatch() {
    Match match = new MatchImpl(matchSet, otherRegex, false);
    assertFalse(re.hasMatch(match));
    re.registerMatch(match);
    assertTrue(re.hasMatch(match));
  }

  /** Arbitrary start of a match. */
  private static final int ARBITRARY_START = 10;

  /** Arbitrary end of a match. */
  private static final int ARBITRARY_END = 20;

  /** Perform a bunch of actions and check that everything seems as they should be when doing so. */
  @Test
  public void testPerformActions() {
    int start = ARBITRARY_START;
    int end = ARBITRARY_END;

    re.add(a);
    assertFalse(actionFired);

    for (int i = 1; i < ARBITRARY_START; i++) {
      actionFired = false;
      timesFired = 0;
      firedBuffer = null;
      firedStart = 0;
      firedEnd = 0;

      re.performActions(b, ++start, ++end);

      assertTrue(actionFired);
      assertEquals(timesFired, 1);
      assertEquals(firedBuffer, b);
      assertEquals(firedStart, start);
      assertEquals(firedEnd, end);
    }
  }
}
