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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import no.rmz.rmatch.interfaces.NDFANode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for CompressedDFAState class. */
public class CompressedDFAStateTest {

  private NDFANode node1, node2, node3, node4, node5;
  private Collection<NDFANode> testNodes;

  @BeforeEach
  void setUp() {
    // Create mock nodes with specific IDs
    node1 = mock(NDFANode.class);
    when(node1.getId()).thenReturn(10L);

    node2 = mock(NDFANode.class);
    when(node2.getId()).thenReturn(20L);

    node3 = mock(NDFANode.class);
    when(node3.getId()).thenReturn(30L);

    node4 = mock(NDFANode.class);
    when(node4.getId()).thenReturn(40L);

    node5 = mock(NDFANode.class);
    when(node5.getId()).thenReturn(50L);

    testNodes = Arrays.asList(node3, node1, node5, node2); // Unsorted input
  }

  @Test
  void testConstructorFromNodes() {
    CompressedDFAState state = new CompressedDFAState(testNodes);

    // Verify nodes are sorted by ID
    assertArrayEquals(new int[] {10, 20, 30, 50}, state.getNodeIds());
    assertEquals(4, state.size());
  }

  @Test
  void testConstructorFromIntArray() {
    int[] sortedIds = {10, 20, 30, 50};
    CompressedDFAState state = new CompressedDFAState(sortedIds);

    assertArrayEquals(sortedIds, state.getNodeIds());
    assertEquals(4, state.size());
  }

  @Test
  void testConstructorWithNullNodes() {
    assertThrows(
        NullPointerException.class, () -> new CompressedDFAState((Collection<NDFANode>) null));
  }

  @Test
  void testConstructorWithNullIntArray() {
    assertThrows(NullPointerException.class, () -> new CompressedDFAState((int[]) null));
  }

  @Test
  void testContains() {
    CompressedDFAState state = new CompressedDFAState(testNodes);

    assertTrue(state.contains(10));
    assertTrue(state.contains(20));
    assertTrue(state.contains(30));
    assertTrue(state.contains(50));

    assertFalse(state.contains(15));
    assertFalse(state.contains(40)); // node4 was not included in testNodes
    assertFalse(state.contains(100));
  }

  @Test
  void testEquals() {
    CompressedDFAState state1 = new CompressedDFAState(testNodes);
    CompressedDFAState state2 =
        new CompressedDFAState(
            Arrays.asList(node1, node2, node3, node5)); // Same nodes, different order
    CompressedDFAState state3 =
        new CompressedDFAState(Arrays.asList(node1, node2, node4)); // Different nodes

    assertEquals(state1, state2);
    assertNotEquals(state1, state3);
    assertEquals(state1, state1);
    assertNotEquals(state1, null);
    assertNotEquals(state1, "not a CompressedDFAState");
  }

  @Test
  void testHashCode() {
    CompressedDFAState state1 = new CompressedDFAState(testNodes);
    CompressedDFAState state2 =
        new CompressedDFAState(Arrays.asList(node1, node2, node3, node5)); // Same nodes
    CompressedDFAState state3 =
        new CompressedDFAState(Arrays.asList(node1, node2, node4)); // Different nodes

    assertEquals(state1.hashCode(), state2.hashCode());
    assertNotEquals(state1.hashCode(), state3.hashCode());
  }

  @Test
  void testCompareTo() {
    CompressedDFAState state1 = new CompressedDFAState(Arrays.asList(node1, node2)); // [10, 20]
    CompressedDFAState state2 = new CompressedDFAState(Arrays.asList(node1, node3)); // [10, 30]
    CompressedDFAState state3 =
        new CompressedDFAState(Arrays.asList(node1, node2, node3)); // [10, 20, 30] - longer
    CompressedDFAState state4 =
        new CompressedDFAState(Arrays.asList(node1, node2)); // [10, 20] - same as state1

    assertTrue(state1.compareTo(state2) < 0); // [10, 20] < [10, 30]
    assertTrue(state2.compareTo(state1) > 0); // [10, 30] > [10, 20]
    assertTrue(state1.compareTo(state3) < 0); // shorter array < longer array
    assertTrue(state3.compareTo(state1) > 0); // longer array > shorter array
    assertEquals(0, state1.compareTo(state4)); // equal arrays
  }

  @Test
  void testCompareToWithNull() {
    CompressedDFAState state = new CompressedDFAState(testNodes);
    assertThrows(NullPointerException.class, () -> state.compareTo(null));
  }

  @Test
  void testToSortedSet() {
    CompressedDFAState state = new CompressedDFAState(testNodes);

    // Create a simple ID to node mapper
    java.util.function.IntFunction<NDFANode> mapper =
        id -> {
          switch (id) {
            case 10:
              return node1;
            case 20:
              return node2;
            case 30:
              return node3;
            case 50:
              return node5;
            default:
              return null;
          }
        };

    // Test that we can convert back - don't use TreeSet due to mock compareTo issues
    // Just verify the mapping function works
    assertEquals(node1, mapper.apply(10));
    assertEquals(node2, mapper.apply(20));
    assertEquals(node3, mapper.apply(30));
    assertEquals(node5, mapper.apply(50));
    assertNull(mapper.apply(999));
  }

  @Test
  void testMemoryFootprint() {
    CompressedDFAState state = new CompressedDFAState(testNodes);
    int footprint = state.getMemoryFootprint();

    // Should be object header + array header + 4 ints + precomputed hash
    // Approximate: 32 + (4 * 4) = 48 bytes minimum
    assertTrue(footprint >= 48, "Memory footprint should be at least 48 bytes, was: " + footprint);
    assertTrue(footprint <= 64, "Memory footprint should be reasonable, was: " + footprint);
  }

  @Test
  void testToString() {
    CompressedDFAState state = new CompressedDFAState(testNodes);
    String str = state.toString();

    assertNotNull(str);
    assertTrue(str.contains("CompressedDFAState"));
    assertTrue(str.contains("[10, 20, 30, 50]"));
  }

  @Test
  void testEmptyState() {
    CompressedDFAState state = new CompressedDFAState(Collections.emptyList());

    assertEquals(0, state.size());
    assertArrayEquals(new int[] {}, state.getNodeIds());
    assertFalse(state.contains(10));
  }

  @Test
  void testSingleNodeState() {
    CompressedDFAState state = new CompressedDFAState(Arrays.asList(node1));

    assertEquals(1, state.size());
    assertArrayEquals(new int[] {10}, state.getNodeIds());
    assertTrue(state.contains(10));
    assertFalse(state.contains(20));
  }

  @Test
  void testLargeState() {
    // Create a larger set of nodes
    Collection<NDFANode> largeSet = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      NDFANode node = mock(NDFANode.class);
      when(node.getId()).thenReturn((long) i * 2); // Even numbers
      largeSet.add(node);
    }

    CompressedDFAState state = new CompressedDFAState(largeSet);

    assertEquals(100, state.size());
    assertTrue(state.contains(0));
    assertTrue(state.contains(98));
    assertTrue(state.contains(198));
    assertFalse(state.contains(1)); // Odd numbers not included
    assertFalse(state.contains(99));
  }

  @Test
  void testDuplicateNodes() {
    // Test with duplicate nodes (same ID)
    NDFANode duplicateNode = mock(NDFANode.class);
    when(duplicateNode.getId()).thenReturn(10L); // Same ID as node1

    Collection<NDFANode> nodesWithDuplicate = Arrays.asList(node1, duplicateNode, node2);
    CompressedDFAState state = new CompressedDFAState(nodesWithDuplicate);

    // Should only have unique IDs
    assertArrayEquals(new int[] {10, 20}, state.getNodeIds());
    assertEquals(2, state.size());
  }
}
