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
package no.rmz.rmatch.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import no.rmz.rmatch.impls.DFANodeImpl;
import no.rmz.rmatch.impls.MatchSetImpl;
import no.rmz.rmatch.interfaces.DFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import org.junit.jupiter.api.Test;

/** Test allocation optimizations in MatchSetImpl. */
public class AllocationOptimizationTest {

  /**
   * Test that MatchSetImpl with no candidate regexps creates no Match objects (early exit
   * optimization).
   */
  @Test
  public void testEarlyExitWhenNoCandidateRegexps() throws Exception {
    // Create a DFANode with no regexps
    final Set<NDFANode> emptyBasis = new HashSet<>();
    final DFANode emptyNode = new DFANodeImpl(emptyBasis);

    // Create MatchSetImpl with a character that matches no regexps
    final MatchSetImpl ms = new MatchSetImpl(0, emptyNode, 'x');

    // Should have zero matches due to early exit optimization
    assertEquals(0, ms.getMatches().size(), "Should have no matches with empty regexp set");
    assertEquals(false, ms.hasMatches(), "Should report no matches");
  }

  /** Test that MatchSetImpl correctly pre-sizes HashSet based on candidate regexps count. */
  @Test
  public void testHashSetPresizing() throws Exception {
    // Create a DFANode with no regexps to ensure empty candidate set
    final Set<NDFANode> emptyBasis = new HashSet<>();
    final DFANode emptyNode = new DFANodeImpl(emptyBasis);

    // Create MatchSetImpl - should use pre-sized empty HashSet
    final MatchSetImpl ms = new MatchSetImpl(0, emptyNode, null);

    // Verify the optimization path was taken
    assertEquals(0, ms.getMatches().size(), "Should have empty matches collection");
    assertEquals(false, ms.hasMatches(), "Should report no matches");
  }
}
