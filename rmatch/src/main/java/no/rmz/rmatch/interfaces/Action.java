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

/**
 * The action interface is used when a match is identified, and the results of it must be
 * communicated to the outside world.
 */
public interface Action {

  /**
   * When a match is found, actions corresponding to that match is triggered.
   *
   * <p>An instance that can perform a match action must implement this interface.
   *
   * @param b The buffer where the match occurred.
   * @param start The first position of the buffer that matches.
   * @param end The last position in the buffer that matches.
   */
  void performMatch(final Buffer b, final int start, final int end);
}
