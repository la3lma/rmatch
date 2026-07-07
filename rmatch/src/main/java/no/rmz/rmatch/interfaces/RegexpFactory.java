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
package no.rmz.rmatch.interfaces;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import no.rmz.rmatch.impls.RegexpImpl;

/**
 * Factory for engine-internal {@link Regexp} instances.
 *
 * <p>Most applications never need this interface. It exists so tests, diagnostics, and custom
 * matcher construction can supply alternative regular-expression implementations. Ordinary code
 * should create matchers through {@code MatcherFactory.newMatcher()} or {@code new MatcherImpl()}.
 */
public interface RegexpFactory {

  /**
   * Create a new regular-expression state object for the supplied pattern text.
   *
   * @param regexpString pattern text
   * @return regular-expression state object
   */
  Regexp newRegexp(final String regexpString);

  /**
   * Default factory used by production matchers.
   *
   * <p>The factory creates {@link RegexpImpl} instances.
   */
  RegexpFactory DEFAULT_REGEXP_FACTORY =
      (final String regexpString) -> {
        checkNotNull(regexpString, "regexpString can't be null");
        return new RegexpImpl(regexpString);
      };
}
