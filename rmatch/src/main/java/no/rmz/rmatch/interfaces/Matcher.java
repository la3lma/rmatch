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

import no.rmz.rmatch.compiler.RegexpParserException;

/** A facade for the components of the matcher engine. */
public interface Matcher {

  /**
   * Add a regular expression to the matcher, and associate it with an action to be run when the
   * expression matches some input.
   *
   * @param r A regular expression.
   * @param a An action to run.
   * @throws no.rmz.rmatch.compiler.RegexpParserException
   */
  void add(final String r, final Action a) throws RegexpParserException;

  /**
   * Remove an association between a regular expression and an action from the matcher.
   *
   * @param r A regular expression.
   * @param a An action.
   */
  void remove(final String r, final Action a);

  /**
   * Match all the regexps that are presently managed by the matcher and run all the corresponding
   * actions when matches are found.
   *
   * @param b a buffer that will provide the input to be matched against.
   */
  void match(final Buffer b);

  /**
   * Get the NodeStorage instance used by this matcher.
   *
   * @return a NodeStorage instance.
   */
  NodeStorage getNodeStorage();

  /**
   * Shut the matcher down nicely. If the matcher has internal threads, or threadpools or anything
   * else that needs an orderly shutdown, then this method will handle that shutdown.
   *
   * @throws InterruptedException when bad things happen.
   */
  void shutdown() throws InterruptedException;
}
