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

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import no.rmz.rmatch.interfaces.NDFANode;

/**
 * Utility class for mapping between NDFANode objects and their compressed integer representations.
 * This enables efficient conversion between legacy SortedSet<NDFANode> and new compressed int[]
 * representations.
 */
public final class NDFANodeIdMapper {

  /** Cache mapping node IDs to NDFANode objects for reverse lookups. */
  private final ConcurrentHashMap<Integer, NDFANode> idToNodeCache = new ConcurrentHashMap<>();

  /** Singleton instance for global access. */
  private static final NDFANodeIdMapper INSTANCE = new NDFANodeIdMapper();

  /** Private constructor for singleton pattern. */
  private NDFANodeIdMapper() {}

  /**
   * Get the singleton instance of the ID mapper.
   *
   * @return the singleton NDFANodeIdMapper instance
   */
  public static NDFANodeIdMapper getInstance() {
    return INSTANCE;
  }

  /**
   * Register an NDFANode in the mapping cache.
   *
   * @param node the NDFANode to register
   */
  public void registerNode(final NDFANode node) {
    if (node != null) {
      idToNodeCache.put(node.getId().intValue(), node);
    }
  }

  /**
   * Register a collection of NDFANodes in the mapping cache.
   *
   * @param nodes the NDFANodes to register
   */
  public void registerNodes(final Collection<NDFANode> nodes) {
    for (final NDFANode node : nodes) {
      registerNode(node);
    }
  }

  /**
   * Get an NDFANode by its ID.
   *
   * @param id the node ID
   * @return the NDFANode with the given ID, or null if not found
   */
  public NDFANode getNodeById(final int id) {
    return idToNodeCache.get(id);
  }

  /**
   * Get an NDFANode by its ID (alias for getNodeById for compatibility).
   *
   * @param id the node ID
   * @return the NDFANode with the given ID, or null if not found
   */
  public NDFANode getNode(final int id) {
    return getNodeById(id);
  }

  /**
   * Create a compressed DFA state from a collection of NDFANodes, automatically registering them.
   *
   * @param ndfaNodes the NDFANodes to compress
   * @return a CompressedDFAState representing the nodes
   */
  public CompressedDFAState createCompressedState(final Collection<NDFANode> ndfaNodes) {
    // Register nodes in the cache for future reverse lookups
    registerNodes(ndfaNodes);
    return new CompressedDFAState(ndfaNodes);
  }

  /**
   * Get the current cache size for monitoring purposes.
   *
   * @return number of nodes currently cached
   */
  public int getCacheSize() {
    return idToNodeCache.size();
  }
}
