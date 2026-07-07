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
package no.rmz.rmatch.utils;

/**
 * Counter categories used by rmatch diagnostics.
 *
 * <p>Each enum constant owns one slot in the shared diagnostic counter array.
 */
public enum CounterType {
  /** Counter for AbstractNDFANode instances created. */
  ABSTRACT_NDFA_NODES("AbstractNDFANodes"),

  /** Counter for cached edges going to an NDFA. */
  CACHED_NDFA_EDGES("Cached edges going to an NDFA."),

  /** Counter for DFANodeImpl instances created. */
  DFA_NODE_IMPL("DFANodeImpl"),

  /** Counter for known DFA edges. */
  KNOWN_DFA_EDGES("Known DFA Edges"),

  /** Counter for MatchImpl instances created. */
  MATCH_IMPL("MatchImpl"),

  /** Counter for MatchSetImpl instances created. */
  MATCH_SET_IMPL("MatchSetImpl");

  private final String legacyName;

  CounterType(final String legacyName) {
    this.legacyName = legacyName;
  }

  /**
   * Return the historical display name for this counter.
   *
   * @return diagnostic display name
   */
  public String getLegacyName() {
    return legacyName;
  }
}
