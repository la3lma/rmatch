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
import static org.mockito.Mockito.*;

import java.util.*;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests to verify compressed DFA state representation is working correctly. */
public class CompressedIntegrationTest {

  private NDFANode node1;
  private NDFANode node2;
  private NDFANode node3;
  private Regexp regexp1;
  private Set<NDFANode> testNodeSet;

  @BeforeEach
  void setUp() {
    regexp1 = mock(Regexp.class);

    node1 = mock(NDFANode.class);
    when(node1.getId()).thenReturn(100L);
    when(node1.getRegexp()).thenReturn(regexp1);

    node2 = mock(NDFANode.class);
    when(node2.getId()).thenReturn(200L);
    when(node2.getRegexp()).thenReturn(regexp1);

    node3 = mock(NDFANode.class);
    when(node3.getId()).thenReturn(300L);
    when(node3.getRegexp()).thenReturn(regexp1);

    testNodeSet = new TreeSet<>(Comparator.comparing(NDFANode::getId));
    testNodeSet.addAll(Arrays.asList(node1, node2, node3));
  }

  @Test
  void testDFANodeImplIntegratesCompressedState() {
    // Create a DFANodeImpl with test nodes
    DFANodeImpl dfaNode = new DFANodeImpl(testNodeSet);

    // Verify compressed basis was created
    CompressedDFAState compressedBasis = dfaNode.getCompressedBasis();
    assertNotNull(compressedBasis, "Compressed basis should not be null");

    // Verify it contains all the node IDs
    assertEquals(3, compressedBasis.size(), "Compressed basis should contain 3 nodes");
    assertTrue(compressedBasis.contains(100), "Should contain node1 ID");
    assertTrue(compressedBasis.contains(200), "Should contain node2 ID");
    assertTrue(compressedBasis.contains(300), "Should contain node3 ID");

    // Verify nodes were registered with mapper
    NDFANodeIdMapper mapper = NDFANodeIdMapper.getInstance();
    assertEquals(node1, mapper.getNodeById(100), "Node1 should be registered");
    assertEquals(node2, mapper.getNodeById(200), "Node2 should be registered");
    assertEquals(node3, mapper.getNodeById(300), "Node3 should be registered");
  }

  @Test
  void testNodeStorageImplUsesCompressedLookup() {
    NodeStorageImpl storage = new NodeStorageImpl();

    // Create first DFA node
    var dfaNode1 = storage.getDFANode(new TreeSet<>(testNodeSet));
    assertNotNull(dfaNode1, "First DFA node should not be null");

    // Request same set again - should return same instance due to compressed lookup
    var dfaNode2 = storage.getDFANode(new TreeSet<>(testNodeSet));
    assertSame(dfaNode1, dfaNode2, "Should return same DFA node instance from cache");

    // Create different set with only node1 (different content)
    SortedSet<NDFANode> differentSet = new TreeSet<>(Comparator.comparing(NDFANode::getId));
    differentSet.add(node1); // Only one node instead of three
    var dfaNode3 = storage.getDFANode(differentSet);

    assertNotSame(dfaNode1, dfaNode3, "Different node sets should produce different DFA nodes");
    assertNotNull(dfaNode3, "Different DFA node should not be null");
    
    // Verify the different node has different compressed state
    DFANodeImpl impl1 = (DFANodeImpl) dfaNode1;
    DFANodeImpl impl3 = (DFANodeImpl) dfaNode3;
    assertNotEquals(impl1.getCompressedBasis(), impl3.getCompressedBasis(), 
        "Different DFA nodes should have different compressed basis");
  }

  @Test
  void testCompressedStateMemoryFootprintIsReasonable() {
    CompressedDFAState compressedState = new CompressedDFAState(testNodeSet);

    // Memory footprint should be reasonable (much less than TreeSet equivalent)
    int footprint = compressedState.getMemoryFootprint();
    assertTrue(
        footprint > 0 && footprint < 200, // Conservative upper bound for 3 integers
        "Memory footprint should be reasonable: " + footprint + " bytes");

    // Test shows compressed representation is working
    assertEquals(3, compressedState.size());
    assertArrayEquals(new int[] {100, 200, 300}, compressedState.getNodeIds());
  }

  @Test
  void testCompressedStateEqualityWorksCorrectly() {
    CompressedDFAState state1 = new CompressedDFAState(testNodeSet);

    // Create different set
    SortedSet<NDFANode> equivalentSet = new TreeSet<>(Comparator.comparing(NDFANode::getId));
    equivalentSet.addAll(testNodeSet);
    CompressedDFAState state2 = new CompressedDFAState(equivalentSet);

    // Should be equal and have same hash code
    assertEquals(state1, state2, "Equivalent compressed states should be equal");
    assertEquals(
        state1.hashCode(), state2.hashCode(), "Equivalent states should have same hash code");

    // Different state should not be equal
    Set<NDFANode> differentSetForComparison = Set.of(node1);
    CompressedDFAState state3 = new CompressedDFAState(differentSetForComparison);
    assertNotEquals(state1, state3, "Different compressed states should not be equal");
  }
}
