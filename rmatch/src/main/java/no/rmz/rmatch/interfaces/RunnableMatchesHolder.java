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
 * Collector for completed match candidates whose actions may be invoked.
 *
 * <p>A match enters this holder only after it reaches a legal terminal state. It may still be
 * suppressed later if another overlapping candidate dominates it.
 */
public interface RunnableMatchesHolder {

  /**
   * Add a completed candidate to the holder.
   *
   * @param m completed match candidate
   */
  void add(final Match m);

  /**
   * Return the candidates currently held for possible execution.
   *
   * @return runnable match candidates
   */
  Set<Match> getMatches();
}
