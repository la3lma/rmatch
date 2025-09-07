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

/**
 * A set used to hold matches that are legal to run since they represent a legal match, but may or
 * may not actually be run pending determination of domination ordering.
 */
public interface RunnableMatchesHolder {

  /**
   * Add a match to the set. Fail if the match isn't final.
   *
   * @param m the match to add.
   */
  void add(final Match m);

  /**
   * Get the set of matches.
   *
   * @return the set of matches.
   */
  Set<Match> getMatches();
}
