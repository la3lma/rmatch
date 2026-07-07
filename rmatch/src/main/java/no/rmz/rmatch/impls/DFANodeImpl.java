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

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.utils.CounterType;
import no.rmz.rmatch.utils.FastCounter;
import no.rmz.rmatch.utils.FastCounters;

/** An implementation of deterministic finite automata nodes DFA. */
public final class DFANodeImpl implements DFANode {

  /**
   * Counter used to figure out both how many DFA nodes are allocated, and to generate unique IDs
   * for the nodes (put in the "id" variable).
   */
  private static final FastCounter COUNTER = FastCounters.newCounter(CounterType.DFA_NODE_IMPL);

  /** A counter for known edges going to other DFAs. */
  private static final FastCounter KNOWN_DFA_EDGES_COUNTER =
      FastCounters.newCounter(CounterType.KNOWN_DFA_EDGES);

  /** The set of regular expression this node represents. */
  private final Set<Regexp> regexps = new HashSet<>();

  /** Read-only cached view of regexps associated with this DFA node. */
  private volatile Set<Regexp> regexpsView;

  /**
   * Maximum number of cached edges per DFA node to prevent memory leaks. This prevents the nextMap
   * from growing unbounded and causing OutOfMemoryError.
   */
  private static final int MAX_CACHED_EDGES = 10000;

  /** Number of characters covered by the ASCII fast-path arrays. */
  private static final int ASCII_LIMIT = 128;

  /**
   * Sentinel marking an ASCII character that is known to have no outgoing transition. Lets us cache
   * negative lookups (the ConcurrentHashMap path cannot store nulls, so it recomputes failing
   * transitions on every visit).
   */
  private static final DFANode NO_TRANSITION = new DFANodeImpl(Collections.emptySet());

  /**
   * Direct-indexed transition table for ASCII characters. Entries are null (not yet computed), a
   * real DFA node, or NO_TRANSITION. Plain array: each DFA node graph is owned by a single matcher,
   * whose match loop is serialized; a lost racy write only causes a benign recomputation.
   */
  private final DFANode[] asciiNext = new DFANode[ASCII_LIMIT];

  /** Context-aware transition cache used only when zero-width assertions are active. */
  private final ConcurrentMap<ContextCharKey, DFANode> contextNextMap = new ConcurrentHashMap<>();

  /** Direct-indexed cache of getRegexpsThatCanStartWith results for ASCII characters. */
  @SuppressWarnings("unchecked")
  private final Set<Regexp>[] asciiStartCache = new Set[ASCII_LIMIT];

  /**
   * True once asciiStartCache holds at least one entry. Lets addRegexp skip the O(128) clear during
   * DFA construction, where addRegexp is called once per basis NDFA node while the cache is still
   * empty.
   */
  private boolean asciiStartCachePopulated = false;

  /**
   * A map of computed edges going out of this node. There may be more edges going out of this node,
   * but these are the nodes that have been encountered so far during matching.
   *
   * <p>Size is limited to MAX_CACHED_EDGES to prevent memory leaks.
   */
  private final ConcurrentMap<Character, DFANode> nextMap = new ConcurrentHashMap<>();

  /** The set of regular expressions for which this node will make a match fail. */
  private final Set<Regexp> isFailingSet = ConcurrentHashMap.newKeySet();

  /** An unique (per VM) id for this DFANode. */
  private final long id;

  /** A cache used to memoize check for finality for particular regexps. */
  private final Map<Regexp, Boolean> baseIsFinalCache = new ConcurrentHashMap<>();

  /** Cache for first-character filtering to optimize O(l*m) bottleneck. */
  private final Map<Character, Set<Regexp>> firstCharRegexpCache = new HashMap<>();

  /** Cache of regexps for which this node is terminal. */
  private volatile Set<Regexp> terminalRegexpsCache;

  /** Compressed representation of the basis set for memory efficiency and faster operations. */
  private final CompressedDFAState compressedBasis;

  /**
   * Cache for reconstructed basis list - only created when needed. This provides the old interface
   * while using compressed storage internally.
   */
  private volatile List<NDFANode> basisList;

  /**
   * Create new DFA based representing a set of NDFA nodes.
   *
   * @param ndfanodeset the set of NDFA nodes the new DFA node should represent.
   */
  @SuppressWarnings("SpellCheckingInspection")
  public DFANodeImpl(final Set<NDFANode> ndfanodeset) {
    // Register nodes and create compressed representation FIRST
    NDFANodeIdMapper.getInstance().registerNodes(ndfanodeset);
    this.compressedBasis = new CompressedDFAState(ndfanodeset);

    initialize(ndfanodeset);

    id = COUNTER.inc();
  }

  /**
   * Initialize the new node based on a set of NDFA nodes. Notify the regexp about this DFA node
   * being active for that expr.
   *
   * @param ndfanodeset the set of NDFA nodes this DFA node is based on.
   */
  private void initialize(final Set<NDFANode> ndfanodeset) {
    for (final NDFANode node : ndfanodeset) {
      checkNotNull(node, "no null nodes allowed!");
      final Regexp r = checkNotNull(node.getRegexp(), "Current regexp can't be null");
      addRegexp(r);
      r.addActive(this);
      if (node.isTerminal()) {
        r.addTerminalNode(this);
      }

      if (node.isFailing()) {
        isFailingSet.add(r);
      }
    }
  }

  /**
   * Return a unique (within this VM) id for this DFANode.
   *
   * @return the id
   */
  @Override
  public long getId() {
    return id;
  }

  @Override
  public boolean isActiveFor(final Regexp r) {
    return r.isActiveFor(this);
  }

  @Override
  public boolean isTerminalFor(final Regexp r) {
    return getTerminalRegexpsCached().contains(r);
  }

  @Override
  public Match newMatch(final MatchSet ms, final Regexp r) {
    return new MatchImpl(ms, r, baseIsFinalFor(r));
  }

  @Override
  public void addRegexp(final Regexp r) {
    regexps.add(r);
    regexpsView = null;
    terminalRegexpsCache = null;
    if (asciiStartCachePopulated) {
      Arrays.fill(asciiStartCache, null);
      asciiStartCachePopulated = false;
    }
  }

  @Override
  public Set<Regexp> getRegexps() {
    Set<Regexp> view = regexpsView;
    if (view == null) {
      synchronized (this) {
        view = regexpsView;
        if (view == null) {
          view = Collections.unmodifiableSet(regexps);
          regexpsView = view;
        }
      }
    }
    return view;
  }

  @Override
  public Set<Regexp> getRegexpsThatCanStartWith(final Character ch) {
    final char c = ch;
    if (c < ASCII_LIMIT) {
      // Lock-free fast path: recomputation on a racy miss is benign and idempotent.
      Set<Regexp> cached = asciiStartCache[c];
      if (cached == null) {
        cached = computeRegexpsThatCanStartWith(ch);
        asciiStartCache[c] = cached;
        asciiStartCachePopulated = true;
      }
      return cached;
    }
    return getRegexpsThatCanStartWithNonAscii(ch);
  }

  @Override
  public Set<Regexp> getRegexpsThatCanStartWith(final Character ch, final MatchContext context) {
    if (context == MatchContext.NONE) {
      return getRegexpsThatCanStartWith(ch);
    }
    final Set<Regexp> filteredRegexps = new HashSet<>();
    for (final Regexp r : regexps) {
      if (r.canStartWith(ch, context)) {
        filteredRegexps.add(r);
      }
    }
    return Collections.unmodifiableSet(filteredRegexps);
  }

  private synchronized Set<Regexp> getRegexpsThatCanStartWithNonAscii(final Character ch) {
    // Return cached result if available
    Set<Regexp> cachedResult = firstCharRegexpCache.get(ch);
    if (cachedResult != null) {
      return cachedResult;
    }

    final Set<Regexp> unmodifiableResult = computeRegexpsThatCanStartWith(ch);
    firstCharRegexpCache.put(ch, unmodifiableResult);
    return unmodifiableResult;
  }

  private Set<Regexp> computeRegexpsThatCanStartWith(final Character ch) {
    final Set<Regexp> filteredRegexps = new HashSet<>();
    for (final Regexp r : regexps) {
      if (r.canStartWith(ch)) {
        filteredRegexps.add(r);
      }
    }
    return Collections.unmodifiableSet(filteredRegexps);
  }

  @Override
  public void addLink(final Character c, final DFANode n) {
    final char ch = c;
    if (ch < ASCII_LIMIT) {
      asciiNext[ch] = n;
    } else {
      nextMap.put(c, n);
    }
  }

  @Override
  public boolean hasLinkFor(final Character c) {
    final char ch = c;
    if (ch < ASCII_LIMIT) {
      final DFANode n = asciiNext[ch];
      return n != null && n != NO_TRANSITION;
    }
    return nextMap.containsKey(c);
  }

  /**
   * Get the next basis for a DFANode by pursuing the current basis through the character. Now uses
   * compressed representation for better performance.
   *
   * @param ch the character to explore.
   * @return A set of NDFANodes that serves as the basis for the next DFANode.
   */
  private SortedSet<NDFANode> getNextThroughBasis(final Character ch) {
    return getNextThroughBasis(ch, MatchContext.NONE);
  }

  private SortedSet<NDFANode> getNextThroughBasis(final Character ch, final MatchContext context) {
    final TreeSet<NDFANode> result = new TreeSet<>();

    // Use compressed representation for better memory efficiency
    final List<NDFANode> basisNodes = getBasisList();
    for (final NDFANode n : basisNodes) {
      result.addAll(context == MatchContext.NONE ? n.getNextSet(ch) : n.getNextSet(ch, context));
    }

    return result;
  }

  @Override
  public DFANode getNext(final Character ch, final NodeStorage ns) {
    final char c = ch;
    if (c < ASCII_LIMIT) {
      // Direct-indexed fast path, including negative caching via NO_TRANSITION.
      final DFANode cached = asciiNext[c];
      if (cached != null) {
        return cached == NO_TRANSITION ? null : cached;
      }
      final DFANode computed = computeNext(ch, ns);
      asciiNext[c] = computed == null ? NO_TRANSITION : computed;
      return computed;
    }

    // Non-ASCII: check if we already have this edge cached
    DFANode cachedNode = nextMap.get(ch);
    if (cachedNode != null) {
      return cachedNode;
    }

    // If cache is full, clear it to prevent memory leaks
    if (nextMap.size() >= MAX_CACHED_EDGES) {
      nextMap.clear();
    }

    return nextMap.computeIfAbsent(ch, key -> computeNext(key, ns));
  }

  @Override
  public DFANode getNext(final Character ch, final NodeStorage ns, final MatchContext context) {
    if (context == MatchContext.NONE) {
      return getNext(ch, ns);
    }
    final ContextCharKey key = new ContextCharKey(ch, context);
    final DFANode cachedNode = contextNextMap.get(key);
    if (cachedNode != null) {
      return cachedNode == NO_TRANSITION ? null : cachedNode;
    }
    final DFANode computed = computeNext(ch, ns, context);
    contextNextMap.put(key, computed == null ? NO_TRANSITION : computed);
    return computed;
  }

  private DFANode computeNext(final Character ch, final NodeStorage ns) {
    return computeNext(ch, ns, MatchContext.NONE);
  }

  private DFANode computeNext(
      final Character ch, final NodeStorage ns, final MatchContext context) {
    KNOWN_DFA_EDGES_COUNTER.inc();

    final SortedSet<NDFANode> nodes = getNextThroughBasis(ch, context);
    if (!nodes.isEmpty()) {
      return ns.getDFANode(nodes);
    }
    return null;
  }

  @Override
  public void removeLink(final Character c) {
    final char ch = c;
    if (ch < ASCII_LIMIT) {
      asciiNext[ch] = null;
    } else {
      nextMap.remove(c);
    }
  }

  /**
   * Calculate if the present DFANode is final for a particular regular expression.
   *
   * @param r a regexp
   * @return true iff this node is final for r.
   */
  private boolean baseIsFinalFor(final Regexp r) {
    return baseIsFinalCache.computeIfAbsent(
        r, key -> getBasisList().parallelStream().anyMatch(key::hasTerminalNdfaNode));
  }

  @Override
  public boolean failsSomeRegexps() {
    return !isFailingSet.isEmpty();
  }

  @Override
  public boolean isFailingFor(final Regexp regexp) {
    return isFailingSet.contains(regexp);
  }

  Set<Regexp> getTerminalRegexpsCached() {
    Set<Regexp> cached = terminalRegexpsCache;
    if (cached == null) {
      synchronized (this) {
        cached = terminalRegexpsCache;
        if (cached == null) {
          final Set<Regexp> computed = new HashSet<>();
          for (final Regexp regexp : regexps) {
            if (regexp.hasTerminalNdfaNode(this)) {
              computed.add(regexp);
            }
          }
          cached = Collections.unmodifiableSet(computed);
          terminalRegexpsCache = cached;
        }
      }
    }
    return cached;
  }

  /**
   * Get the compressed representation of this DFA node's basis set. Package-private for use by
   * NodeStorageImpl.
   *
   * @return the compressed DFA state representation
   */
  CompressedDFAState getCompressedBasis() {
    return compressedBasis;
  }

  /**
   * Get the basis nodes as a list, reconstructed from compressed storage. Uses double-checked
   * locking for thread-safe lazy initialization.
   *
   * @return list of NDFANode objects that form this DFA node's basis
   */
  private List<NDFANode> getBasisList() {
    if (basisList == null) {
      synchronized (this) {
        if (basisList == null) {
          // Reconstruct from compressed state
          final NDFANodeIdMapper mapper = NDFANodeIdMapper.getInstance();
          final List<NDFANode> reconstructed = new ArrayList<>();
          for (final int nodeId : compressedBasis.getNodeIds()) {
            final NDFANode node = mapper.getNodeById(nodeId);
            if (node != null) {
              reconstructed.add(node);
            }
          }
          this.basisList = reconstructed;
        }
      }
    }
    return basisList;
  }

  private record ContextCharKey(Character ch, MatchContext context) {}
}
