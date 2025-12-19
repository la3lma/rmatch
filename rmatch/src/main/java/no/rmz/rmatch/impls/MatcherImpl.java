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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.*;

/**
 * An implementation of the Matcher interface that hooks together various component that together
 * makes up a real matcher.
 */
public final class MatcherImpl implements Matcher {

  /** The regexp storage we will use. */
  private final RegexpStorage rs;

  /** Our precious MatchEngine. */
  private final MatchEngine me;

  /** Engine type from system property. Default "fastpath" is optimal based on comprehensive testing. */
  private final String engineType = System.getProperty("rmatch.engine", "fastpath");

  /** Flag to enable Bloom filter optimization. */
  private final boolean useBloomFilter = "bloom".equalsIgnoreCase(engineType);

  /** Flag to enable fast-path optimization. */
  private final boolean useFastPath = "fastpath".equalsIgnoreCase(engineType);

  /** Our equally prescious NodeStorage. Our precioussss. */
  private final NodeStorage ns;

  /** Indicates that the engine-specific prefilter needs to be rebuilt. */
  private volatile boolean prefilterDirty = false;

  /**
   * Create a new matcher using the default compiler and regexp factory. This is usually a good
   * choice for production use.
   */
  public MatcherImpl() {
    this(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
  }

  /**
   * This is useful for testing when we sometimes want to inject mocked compilers and regexp
   * factories.
   *
   * @param compiler A compiler to use.
   * @param regexpFactory A regexp factory to use.
   */
  public MatcherImpl(final NDFACompiler compiler, final RegexpFactory regexpFactory) {
    /** The compiler we will use. */
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

  @Override
  public void add(final String r, final Action a) throws RegexpParserException {
    synchronized (rs) {
      rs.add(r, a);

      if (needsPrefilterConfiguration()) {
        prefilterDirty = true;
      }
    }
  }

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

  @Override
  public void match(final Buffer b) {
    ensurePrefilterConfigured();

    synchronized (me) {
      me.match(b);
    }
  }

  @Override
  public NodeStorage getNodeStorage() {
    return ns;
  }

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
