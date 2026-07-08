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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.*;

/**
 * Single-engine {@link Matcher} implementation used by partitioned matchers internally.
 *
 * <p>Application code should create matchers through {@link MatcherFactory}. The default
 * constructor uses the fast-path engine unless the {@code rmatch.engine} system property selects
 * another engine variant.
 *
 * <p>Callbacks receive inclusive start/end offsets. Use {@code buffer.getString(start, end + 1)} to
 * recover the matched text.
 */
final class MatcherImpl implements Matcher {

  /** Storage for registered expressions. */
  private final RegexpStorage rs;

  /** Engine used to scan buffers. */
  private final MatchEngine me;

  /**
   * Engine type from system property. Default "fastpath" is optimal based on comprehensive testing.
   */
  private final String engineType = System.getProperty("rmatch.engine", "fastpath");

  /** Flag to enable Bloom filter optimization. */
  private final boolean useBloomFilter = "bloom".equalsIgnoreCase(engineType);

  /** Flag to enable fast-path optimization. */
  private final boolean useFastPath = "fastpath".equalsIgnoreCase(engineType);

  /** Node storage used by the matcher engine. */
  private final NodeStorage ns;

  /** Indicates that the engine-specific prefilter needs to be rebuilt. */
  private volatile boolean prefilterDirty = false;

  /** Create a new matcher using the default compiler, regexp factory, and engine selection. */
  MatcherImpl() {
    this(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
  }

  /**
   * Create a matcher with explicitly supplied compiler and regexp factory.
   *
   * <p>This constructor is primarily useful for tests and experiments. Normal users should prefer
   * {@link MatcherFactory#newMatcher()} or {@link MatcherFactory#newSingleMatcher()}.
   *
   * @param compiler compiler used to turn registered expressions into automata
   * @param regexpFactory factory used to create internal regexp objects
   */
  MatcherImpl(final NDFACompiler compiler, final RegexpFactory regexpFactory) {
    NDFACompiler compiler1 = checkNotNull(compiler);
    checkNotNull(regexpFactory);
    ns = new NodeStorageImpl();
    rs = new RegexpStorageImpl(ns, compiler, regexpFactory);
    if (useBloomFilter) {
      me = new BloomFilterMatchEngine(ns);
    } else if (useFastPath) {
      me = new FastPathMatchEngine(ns);
    } else {
      me = new MatchEngineImpl(ns);
    }

    prefilterDirty = needsPrefilterConfiguration();
  }

  /**
   * Register an expression/action pair.
   *
   * @param r regular-expression text in the supported rmatch syntax subset
   * @param a action to run for each match
   * @throws RegexpParserException if {@code r} cannot be parsed
   */
  @Override
  public void add(final String r, final Action a) throws RegexpParserException {
    synchronized (rs) {
      rs.add(r, a);

      if (needsPrefilterConfiguration()) {
        prefilterDirty = true;
      }
    }
  }

  /**
   * Remove an expression/action pair from this matcher.
   *
   * @param r regular-expression text previously registered with this matcher
   * @param a action previously associated with {@code r}
   */
  @Override
  public void remove(final String r, final Action a) {
    synchronized (rs) {
      rs.remove(r, a);

      if (needsPrefilterConfiguration()) {
        prefilterDirty = true;
      }
    }
  }

  /**
   * Configure the prefilter for FastPathMatchEngine.
   *
   * <p>This builds pattern mappings and enables literal-based prefiltering for the fast-path
   * engine.
   */
  private void configurePrefilterForEngine(final FastPathMatchEngine fpEngine) {
    // Build pattern ID to regex string mapping
    final Set<String> regexpStrings = rs.getRegexpSet();
    final int size = regexpStrings.size();
    final Map<Integer, String> patterns = new HashMap<>(size);
    final Map<Integer, Integer> flags = new HashMap<>(size);
    final Map<String, Regexp> regexpMappings = new HashMap<>(size);

    int patternId = 0;

    for (final String regexpStr : regexpStrings) {
      patterns.put(patternId, regexpStr);
      flags.put(patternId, 0);
      regexpMappings.put(regexpStr, rs.getRegexp(regexpStr));
      patternId++;
    }

    // Configure the prefilter
    fpEngine.configurePrefilter(patterns, flags, regexpMappings);
  }

  /**
   * Configure the AhoCorasick prefilter for the legacy MatchEngineImpl. This builds pattern
   * mappings and enables aggressive literal-based prefiltering.
   */
  private void configurePrefilterForLegacyEngine() {
    final MatchEngineImpl legacyEngine = (MatchEngineImpl) me;

    // Build pattern ID to regex string mapping
    final Set<String> regexpStrings = rs.getRegexpSet();
    final int size = regexpStrings.size();
    final Map<Integer, String> patterns = new HashMap<>(size);
    final Map<Integer, Integer> flags = new HashMap<>(size);
    final Map<String, Regexp> regexpMappings = new HashMap<>(size);

    int patternId = 0;

    for (final String regexpStr : regexpStrings) {
      patterns.put(patternId, regexpStr);
      flags.put(
          patternId, 0); // Default flags, could be enhanced to detect case-insensitive patterns
      regexpMappings.put(regexpStr, rs.getRegexp(regexpStr));
      patternId++;
    }

    // Configure the prefilter with our patterns and mappings
    legacyEngine.configurePrefilter(patterns, flags, regexpMappings);
  }

  /**
   * Scan the supplied buffer and invoke actions for matching expressions.
   *
   * <p>This implementation synchronizes access to the underlying engine while matching. Do not
   * mutate the registered expression set from another thread while relying on deterministic match
   * timing.
   *
   * @param b input buffer to scan
   */
  @Override
  public void match(final Buffer b) {
    ensurePrefilterConfigured();

    synchronized (me) {
      me.match(b);
    }
  }

  /** Release matcher resources. This single-engine implementation currently owns no worker pool. */
  @Override
  public void shutdown() {}

  /**
   * Configure engine-specific prefilters on-demand. This avoids rebuilding heavy data structures
   * for every single addition/removal when callers batch pattern registration.
   */
  private void ensurePrefilterConfigured() {
    if (!prefilterDirty || !needsPrefilterConfiguration()) {
      return;
    }

    synchronized (rs) {
      if (!prefilterDirty) {
        return;
      }

      // Initialize engine-specific optimizations
      if (useBloomFilter && me instanceof BloomFilterMatchEngine bfEngine) {
        final java.util.Set<Regexp> regexps = new java.util.HashSet<>();
        for (final String regexpStr : rs.getRegexpSet()) {
          regexps.add(rs.getRegexp(regexpStr));
        }
        bfEngine.initialize(regexps);
      } else if (useFastPath && me instanceof FastPathMatchEngine fpEngine) {
        // Configure prefilter for fast-path engine
        configurePrefilterForEngine(fpEngine);
      } else if (me instanceof MatchEngineImpl) {
        // Configure AhoCorasick prefilter for legacy engine
        configurePrefilterForLegacyEngine();
      }

      prefilterDirty = false;
    }
  }

  /** Returns true if the current engine variant requires prefilter configuration. */
  private boolean needsPrefilterConfiguration() {
    return useBloomFilter || useFastPath || me instanceof MatchEngineImpl;
  }
}
