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

/**
 * A compiler for regular expressions.
 */
public interface NDFACompiler {

    /**
     * Compile a regular expression, using a RegexpStorage, and return an
     * NDFANode instance that represents the entry point for the compiled
     * expression.
     *
     * @param regexp A regular expression.
     * @param rs A regexp storage.
     * @return An NDFANode that will match the regexp.
     * @throws RegexpParserException when the regexp doesn't parse.
     */
    NDFANode compile(final Regexp regexp, final RegexpStorage rs)
            throws RegexpParserException;
}
