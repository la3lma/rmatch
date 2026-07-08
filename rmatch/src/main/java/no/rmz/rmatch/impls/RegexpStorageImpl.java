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
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.*;

/** In-memory {@link RegexpStorage} implementation used by production matchers. */
final class RegexpStorageImpl implements RegexpStorage {

  /** Map from pattern text to compiled expression state. */
  private final Map<String, Regexp> regexps = new HashMap<>();

  /** Node storage used when compiled expressions create new NDFA nodes. */
  private final NodeStorage storage;

  /** Compiler used to turn pattern strings into NDFA nodes. */
  private final NDFACompiler compiler;

  /** A factory that will produce a Regexp instance for our strings. */
  private final RegexpFactory regexpFactory;

  /**
   * Create storage using the default regular-expression factory.
   *
   * @param storage node storage to receive compiled expressions
   * @param compiler compiler used to compile expressions
   */
  public RegexpStorageImpl(final NodeStorage storage, final NDFACompiler compiler) {
    this(storage, compiler, RegexpFactory.DEFAULT_REGEXP_FACTORY);
  }

  /**
   * Create storage with an explicit regular-expression factory.
   *
   * @param storage node storage to receive compiled expressions
   * @param compiler compiler used to compile expressions
   * @param regexpFactory factory used to create expression state objects
   */
  public RegexpStorageImpl(
      final NodeStorage storage, final NDFACompiler compiler, final RegexpFactory regexpFactory) {
    this.storage = checkNotNull(storage, "Null storage is meaningless");
    this.compiler = checkNotNull(compiler, "Null compiler is meaningless");
    this.regexpFactory = checkNotNull(regexpFactory, "regexpFactory can't be null");
  }

  @Override
  public boolean hasRegexp(final String regexp) {
    synchronized (regexps) {
      return regexps.containsKey(regexp);
    }
  }

  @Override
  public Regexp getRegexp(final String regexpString) {
    synchronized (regexps) {
      Regexp r = regexps.get(regexpString);
      if (r == null) {
        r = regexpFactory.newRegexp(regexpString);
        regexps.put(regexpString, r);
      }
      return r;
    }
  }

  @Override
  public void add(final String regexpString, final Action action) throws RegexpParserException {
    synchronized (regexps) {
      final Regexp r = getRegexp(regexpString);
      r.add(action);

      if (!r.isCompiled()) {
        final NDFANode n = compiler.compile(r, this);
        assert (n != null);
        r.setMyNDFANode(n);
        storage.addToStartnode(n);
        if (r.usesContextAssertions()) {
          storage.markContextAssertionsUsed();
        }
      }
    }
  }

  @Override
  public void remove(final String rexp, final Action a) {
    synchronized (regexps) {
      final Regexp r = getRegexp(rexp);

      r.remove(a);
      if (!r.hasActions()) {
        regexps.remove(rexp);
      }
    }
  }

  @Override
  public Set<String> getRegexpSet() {
    synchronized (regexps) {
      return regexps.keySet();
    }
  }
}
