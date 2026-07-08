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

import no.rmz.rmatch.interfaces.Match;
import no.rmz.rmatch.interfaces.MatchSet;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.utils.CounterType;
import no.rmz.rmatch.utils.FastCounter;
import no.rmz.rmatch.utils.FastCounters;

/** Default {@link Match} implementation used by the engine. */
final class MatchImpl implements Match {
  /**
   * A counter that is increased every time a new MatchImpl is generated. Used for logging and
   * benchmarking.
   */
  private static final FastCounter counter = FastCounters.newCounter(CounterType.MATCH_IMPL);

  /**
   * True iff the match is final, in the sense that its current state represents a legal termination
   * of the match.
   */
  private boolean isFinal;

  /** True iff this match can expect to progress further. */
  private boolean isActive;

  /**
   * The end position of the match. The start position can be found indirectly through the MatchSet
   * ms.
   */
  private int end;

  /**
   * The largest end position at which this match was in a final state, or -1 if it never was.
   *
   * <p>KB-5: a match can pass through a final state and then keep extending while alive but
   * non-final (e.g. "[^a]+[cb]" where the [^a]+ loop consumes past the [cb] ender). When such a
   * match eventually dies non-final, the correct result is the LONGEST end that was final — which
   * must therefore be remembered, not just the instantaneous final flag. Without this, the match
   * was silently discarded.
   */
  private int lastFinalEnd = -1;

  /** Expression being matched by this candidate. */
  private final Regexp r;

  /** Match set that owns this candidate. */
  private final MatchSet ms;

  /** Unique identifier for this candidate. */
  private final long id;

  /**
   * Create a match candidate.
   *
   * @param ms owning match set
   * @param r expression being matched
   * @param isFinal whether the candidate starts in a final state
   */
  public MatchImpl(final MatchSet ms, final Regexp r, final boolean isFinal) {
    this.ms = checkNotNull(ms, "MatchSet can't be null");
    this.r = checkNotNull(r, "Regexp can't be null");
    this.isFinal = isFinal; // why?
    end = ms.getStart(); // XXX Bogus
    if (isFinal) {
      lastFinalEnd = end;
    }
    isActive = true;
    r.registerMatch(this);
    id = counter.inc();
  }

  /** Implement hashing based on the unique ID. */
  @Override
  public int hashCode() {
    return Long.hashCode(this.id);
  }

  /**
   * Create a match candidate that is not initially final.
   *
   * @param ms owning match set
   * @param r expression being matched
   */
  public MatchImpl(final MatchSet ms, final Regexp r) {
    this(ms, r, false); //  By default matches are not final
  }

  @Override
  public String toString() {
    return "[Match  regexpString = '"
        + r.getRexpString()
        + "' start = "
        + ms.getStart()
        + " end = "
        + end
        + " isFinal = "
        + isFinal
        + " isActive "
        + isActive
        + "]";
  }

  @Override
  public long getId() {
    return id;
  }

  @Override
  public void abandon(final Character currentChar) {
    // TODO: Make optional, only run when debugging. checkState(!isAbandoned());
    this.r.abandonMatch(this, currentChar);
    this.isActive = false;
  }

  @Override
  public boolean isAbandoned() {
    return !(isActive || r.hasMatch(this));
  }

  @Override
  public MatchSet getMatchSet() {
    return ms;
  }

  @Override
  public void setIsFinal() {
    isFinal = true;
    lastFinalEnd = end;
  }

  @Override
  public int getStart() {
    return ms.getStart();
  }

  @Override
  public int getEnd() {
    // If the match extended past its last final state without reaching another one, the
    // reportable extent is the last FINAL end (KB-5).
    if (!isFinal && lastFinalEnd >= 0) {
      return lastFinalEnd;
    }
    return end;
  }

  @Override
  public void setEnd(final int end) {
    // NOTE: does NOT update lastFinalEnd — at the time setEnd runs, the final flag still
    // reflects the PREVIOUS position. Only setFinal/setIsFinal carry a fresh judgment.
    this.end = end;
  }

  @Override
  public Regexp getRegexp() {
    return r;
  }

  @Override
  public boolean isActive() {
    return isActive;
  }

  @Override
  public boolean isFinal() {
    // A match that has EVER been final has a committable result (at lastFinalEnd), even if its
    // current extent is not final (KB-5).
    return isFinal || lastFinalEnd >= 0;
  }

  @Override
  public void setNotFinal() {
    isFinal = false;
  }

  @Override
  public void setInactive() {
    isActive = false;
  }

  @Override
  public void setActive(final boolean active) {
    this.isActive = active;
  }

  @Override
  public void setFinal(final boolean aFinal) {
    this.isFinal = aFinal;
    if (aFinal) {
      lastFinalEnd = end;
    }
  }
}
