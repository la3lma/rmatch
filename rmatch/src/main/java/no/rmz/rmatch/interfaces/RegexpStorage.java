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

import java.util.Set;
import no.rmz.rmatch.Action;
import no.rmz.rmatch.RegexpParserException;

/**
 * Engine-internal registry from pattern text to compiled regular-expression state.
 *
 * <p>The lookup is syntactic, not semantic. Two strings that describe the same language, such as
 * {@code aa*} and {@code a+}, are stored as distinct entries unless the caller supplied exactly the
 * same pattern text.
 */
public interface RegexpStorage {

  /**
   * Return whether this storage already contains the supplied pattern text.
   *
   * @param regexp regular-expression text
   * @return {@code true} if a compiled representation is present
   */
  boolean hasRegexp(final String regexp);

  /**
   * Return the compiled representation for a pattern, creating it if necessary.
   *
   * @param regexp regular-expression text
   * @return compiled regular-expression state for {@code regexp}
   */
  Regexp getRegexp(final String regexp);

  /**
   * Associate an action with a regular expression, creating the compiled representation if needed.
   *
   * @param regexp regular-expression text
   * @param a action to invoke when the expression matches
   * @throws RegexpParserException if {@code regexp} is not valid rmatch syntax
   */
  void add(final String regexp, final Action a) throws RegexpParserException;

  /**
   * Return the pattern strings known to this storage.
   *
   * @return stored regular-expression strings
   */
  Set<String> getRegexpSet();
}
