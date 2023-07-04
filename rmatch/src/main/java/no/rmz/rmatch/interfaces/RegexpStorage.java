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

package no.rmz.rmatch.interfaces;

import no.rmz.rmatch.compiler.RegexpParserException;

import java.util.Set;

/**
 * Storage for regular expressions. It provides an interface for storing regular
 * expressions, as strings, and retrieving their corresponding representations
 * as Regexpr instances.
 * <p>
 * The lookup is syntactic, not semantic, so that equivalent regular expression
 * strings, such as "aa*" and "a+" will not be recognized as representing the
 * same regular language, even if they in fact do that.
 */
public interface RegexpStorage {

    /**
     * True iff we have stored a representation corresponding to the regular
     * expression string given as parameter.
     *
     * @param regexp a regular expression string.
     * @return true iff a representation is stored for regexp.
     */
    boolean hasRegexp(final String regexp);

    /**
     * Get the representation representing a regexp string.
     *
     * @param regexp a regular expression string. If no representation is stored
     * prior to the invocation of getRegexp, a representation will be added
     * through the invocation of getRegexp.
     *
     * @return A Regexp instance representing the regexp string.
     */
    Regexp getRegexp(final String regexp);

    /**
     * Add an action associated with a regular expression. If the regular
     * expression isn't represented by a Regexp representation prior to the
     * invocation of the "add" method, a representation will be created by this
     * invocation.
     *
     * @param regexp A regular expression string.
     * @param a An action to be invoked when the regular expression matches.
     * @throws RegexpParserException Thrown when the regexp has a syntax error.
     */
    void add(final String regexp, final Action a) throws RegexpParserException;

    /**
     * Remove the associaten between a regular expression and an action.
     *
     * @param regexp a regular expression
     * @param a An action
     */
    void remove(final String regexp, final Action a);

    /**
     * Get the set of regular expression associated with this storage instance.
     *
     * @return A set of regular expression representations.
     */
    Set<String> getRegexpSet();
}
