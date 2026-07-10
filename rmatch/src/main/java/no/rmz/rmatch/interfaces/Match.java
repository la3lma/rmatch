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

import java.util.Comparator;

/**
 * Engine-internal representation of one in-progress or committed match candidate.
 *
 * <p>This type is public because older engine and diagnostic APIs expose matcher state. Normal
 * application code should not need to create or manipulate {@code Match} instances; use {@link
 * Matcher#add(String, Action)} and handle matches through {@link Action#performMatch(Buffer, long,
 * long)} instead. The start and end offsets exposed here use the engine-internal inclusive
 * convention; note that public action callbacks receive half-open {@code [start, end)} offsets.
 */
public interface Match {

  /**
   * Return the matcher-local identifier for this match candidate.
   *
   * @return unique match identifier
   */
  long getId();

  /**
   * Abandon this candidate and remove it from the match structures that currently reference it.
   *
   * @param currentChar character being processed when the candidate is abandoned
   */
  void abandon(Character currentChar);

  /**
   * Return whether this candidate has been abandoned.
   *
   * @return {@code true} if the candidate is no longer eligible to match
   */
  boolean isAbandoned();

  /**
   * Return the match set that owns this candidate.
   *
   * @return owning match set
   */
  MatchSet getMatchSet();

  /**
   * Return the compiled expression being matched by this candidate.
   *
   * @return compiled regular-expression state
   */
  Regexp getRegexp();

  /**
   * Return the inclusive end offset of this candidate.
   *
   * @return zero-based inclusive end offset
   */
  int getEnd();

  /**
   * Return the inclusive start offset of this candidate.
   *
   * @return zero-based inclusive start offset
   */
  int getStart();

  /**
   * Return whether this candidate is still being extended by the engine.
   *
   * @return {@code true} if the candidate is active
   */
  boolean isActive();

  /**
   * Return whether this candidate currently represents a complete match.
   *
   * @return {@code true} if the candidate is at a valid terminal state
   */
  boolean isFinal();

  /**
   * Set the current end position of the match.
   *
   * @param end the end position
   */
  void setEnd(int end);

  /** Mark this candidate as complete. */
  void setIsFinal();

  /** Mark this candidate as incomplete. */
  void setNotFinal();

  /** Mark this candidate as no longer active. */
  void setInactive();

  /**
   * Set whether this candidate is active.
   *
   * @param activityState new activity state
   */
  void setActive(boolean activityState);

  /**
   * Set whether this candidate is complete.
   *
   * @param finalityState new finality state
   */
  void setFinal(boolean finalityState);

  /** A comparator that compares matches based on their unique identifier. */
  Comparator<Match> COMPARE_BY_OBJECT_ID =
      (t, t1) -> {
        // return Long.signum(t.getId() - t1.getId());
        final long l1 = t.getId();
        final long l2 = t1.getId();
        return Long.compare(l1, l2);
      };

  /** A comparator that compares matches based on their domination status. */
  Comparator<Match> COMPARE_BY_DOMINATION =
      (final Match ths, final Match that) -> {
        if (ths == that) {
          return 0;
        } else if (ths.getRegexp() != that.getRegexp()) {
          return 0;
        } else if ((ths.getStart() == that.getStart()) && (ths.getEnd() == that.getEnd())) {
          return 0;
        } else if ((ths.getStart() <= that.getStart()) && (ths.getEnd() >= that.getEnd())) {
          return -1;
        } else if ((ths.getStart() >= that.getStart()) && (ths.getEnd() <= that.getEnd())) {
          return 1;
        } else {
          return 0;
        }
      };

  /** Return whether this candidate consumes no input characters. */
  default boolean isZeroLength() {
    return (this.getEnd() - this.getStart()) == 0;
  }

  /** Return whether this candidate must keep running before it can be committed. */
  default boolean notReadyForCommit() {
    return !isFinal() || isActive();
  }
}
