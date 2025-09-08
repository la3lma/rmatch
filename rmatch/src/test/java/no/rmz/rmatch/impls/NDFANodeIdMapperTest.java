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

import java.util.Arrays;
import java.util.Collection;
import no.rmz.rmatch.interfaces.NDFANode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for NDFANodeIdMapper class. */
public class NDFANodeIdMapperTest {

  private NDFANodeIdMapper mapper;
  private NDFANode node1, node2, node3;

  @BeforeEach
  void setUp() {
    // Get a fresh mapper instance (note: it's a singleton)
    mapper = NDFANodeIdMapper.getInstance();

    // Create mock nodes
    node1 = mock(NDFANode.class);
    when(node1.getId()).thenReturn(100L);

    node2 = mock(NDFANode.class);
    when(node2.getId()).thenReturn(200L);

    node3 = mock(NDFANode.class);
    when(node3.getId()).thenReturn(300L);
  }

  @Test
  void testSingletonInstance() {
    NDFANodeIdMapper instance1 = NDFANodeIdMapper.getInstance();
    NDFANodeIdMapper instance2 = NDFANodeIdMapper.getInstance();

    assertSame(instance1, instance2);
  }

  @Test
  void testRegisterNode() {
    mapper.registerNode(node1);

    assertEquals(node1, mapper.getNodeById(100));
    assertNull(mapper.getNodeById(999));
  }

  @Test
  void testRegisterNullNode() {
    int initialCacheSize = mapper.getCacheSize();
    mapper.registerNode(null);

    // Should handle null gracefully without changing cache
    assertEquals(initialCacheSize, mapper.getCacheSize());
  }

  @Test
  void testRegisterNodes() {
    Collection<NDFANode> nodes = Arrays.asList(node1, node2, node3);
    mapper.registerNodes(nodes);

    assertEquals(node1, mapper.getNodeById(100));
    assertEquals(node2, mapper.getNodeById(200));
    assertEquals(node3, mapper.getNodeById(300));
    assertNull(mapper.getNodeById(999));
  }

  @Test
  void testCreateCompressedState() {
    Collection<NDFANode> nodes = Arrays.asList(node2, node1, node3); // Unsorted input
    CompressedDFAState state = mapper.createCompressedState(nodes);

    assertNotNull(state);
    assertEquals(3, state.size());
    assertArrayEquals(new int[] {100, 200, 300}, state.getNodeIds());

    // Verify nodes were registered
    assertEquals(node1, mapper.getNodeById(100));
    assertEquals(node2, mapper.getNodeById(200));
    assertEquals(node3, mapper.getNodeById(300));
  }

  @Test
  void testGetCacheSize() {
    // Since mapper is a singleton, we can't control the initial state
    // Just verify that registering unique nodes increases the cache size appropriately
    int initialSize = mapper.getCacheSize();

    // Use unique node IDs to ensure they're actually added
    NDFANode uniqueNode1 = mock(NDFANode.class);
    when(uniqueNode1.getId()).thenReturn(900001L);
    
    NDFANode uniqueNode2 = mock(NDFANode.class);
    when(uniqueNode2.getId()).thenReturn(900002L);

    mapper.registerNode(uniqueNode1);
    assertTrue(mapper.getCacheSize() >= initialSize + 1);

    mapper.registerNode(uniqueNode2);
    assertTrue(mapper.getCacheSize() >= initialSize + 2);

    // Registering same node again shouldn't increase cache size
    int beforeDuplicate = mapper.getCacheSize();
    mapper.registerNode(uniqueNode1);
    assertEquals(beforeDuplicate, mapper.getCacheSize());
  }

  @Test
  void testGetNodeByIdAfterRegistration() {
    // Note: mapper is singleton so may already have nodes registered
    mapper.registerNode(node1);
    assertEquals(node1, mapper.getNodeById(100));

    // Test with different IDs
    mapper.registerNode(node2);
    assertEquals(node2, mapper.getNodeById(200));
    assertEquals(node1, mapper.getNodeById(100)); // Should still be there

    assertNull(mapper.getNodeById(999)); // Non-existent ID
  }

  @Test
  void testRegisterDuplicateNodes() {
    // Use unique IDs to avoid conflicts with singleton state
    NDFANode uniqueNode1 = mock(NDFANode.class);
    when(uniqueNode1.getId()).thenReturn(800001L);
    
    NDFANode duplicateNode = mock(NDFANode.class);
    when(duplicateNode.getId()).thenReturn(800001L); // Same ID as uniqueNode1

    int initialSize = mapper.getCacheSize();

    mapper.registerNode(uniqueNode1);
    mapper.registerNode(duplicateNode);

    // Cache size should only increase by 1 since they have the same ID
    assertEquals(initialSize + 1, mapper.getCacheSize());

    // The second registration should overwrite the first
    assertEquals(duplicateNode, mapper.getNodeById(800001));
  }

  @Test
  void testConcurrentAccess() {
    // Basic test to ensure thread safety of ConcurrentHashMap
    Collection<NDFANode> nodes = Arrays.asList(node1, node2, node3);

    // This is mainly to verify no exceptions are thrown
    assertDoesNotThrow(
        () -> {
          mapper.registerNodes(nodes);
          mapper.getNodeById(100);
          mapper.getCacheSize();
          mapper.createCompressedState(Arrays.asList(node1, node2));
        });
  }

  @Test
  void testIntegrationWithCompressedState() {
    // Test the full cycle: register nodes -> create compressed state -> verify mapping
    Collection<NDFANode> originalNodes = Arrays.asList(node1, node2, node3);
    CompressedDFAState compressedState = mapper.createCompressedState(originalNodes);

    // Create a function to map back from IDs to nodes
    java.util.function.IntFunction<NDFANode> idToNodeFunction = id -> mapper.getNodeById(id);

    // Verify the compressed state has the right structure
    assertEquals(3, compressedState.size());
    assertArrayEquals(new int[] {100, 200, 300}, compressedState.getNodeIds());

    // Verify mapping works both ways
    assertEquals(node1, idToNodeFunction.apply(100));
    assertEquals(node2, idToNodeFunction.apply(200));
    assertEquals(node3, idToNodeFunction.apply(300));
  }
}
