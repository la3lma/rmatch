/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.compiler;

/**
 * Exception thrown when we discover some error during parsing of a regular
 * expression.
 */
public final class RegexpParserException extends Exception {

    /**
     * Something went bad when parsing a regexp.
     * @param msg an explanation of what went wrong.
     */
    public RegexpParserException(final String msg) {
        super(msg);
    }

    /**
     * An exception caused the parsing to go wrong, this is
     * that exception wrapped as a RegexpParserException.
     * @param e the cause of evil.
     */
    public RegexpParserException(final Exception e) {
        super(e);
    }
}
