/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.impls;

import static com.google.common.base.Preconditions.checkArgument;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import no.rmz.rmatch.interfaces.Match;

/**
 * A holder for runnable matches.
 */
public final class RunnableMatchesHolderImpl implements RunnableMatchesHolder {

    private final Set<Match> matches;

    public RunnableMatchesHolderImpl() {
        this.matches = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    /**
     * Add a match to the set. Fail if the match isn't final.
     *
     * @param m the match to add.
     */
    @Override
    public void add(final Match m) {
        checkArgument(m.isFinal());
        matches.add(m);
    }

    /**
     * Get the set of matches.
     *
     * @return the set of matches.
     */
    @Override
    public Set<Match> getMatches() {
        return Collections.unmodifiableSet(matches);
    }

    /**
     * True iff we hold any matches.
     *
     * @return true iff we hold any matches.
     */
    boolean hasMatches() {
        return !matches.isEmpty();
    }

    @Override
    public String toString() {
        return "RunnableMatchesHolder[" + matches + "]";
    }
}
