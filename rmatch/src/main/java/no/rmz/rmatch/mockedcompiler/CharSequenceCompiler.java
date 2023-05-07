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

package no.rmz.rmatch.mockedcompiler;

import no.rmz.rmatch.compiler.CharNode;
import no.rmz.rmatch.compiler.TerminalNode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * A simple compiler for character sequences only to be used during testing.
 */
public final class CharSequenceCompiler {

    /**
     * Compile a string to represent a regexp as part of a Regexp instance.
     *
     * @param regexp the regexp we're part of
     * @param str the string that should be compiled.
     * @return The entry point for the compiled regexp string.
     */
    public static NDFANode compile(final Regexp regexp, final String str) {
        NDFANode result = new TerminalNode(regexp);
        for (int i = str.length() - 1; i >= 0; i--) {
            final Character myChar = str.charAt(i);
            result = new CharNode(result, myChar, regexp);
        }

        return result;
    }

    /**
     * This is an utility class so no public constructor.
     */
    private CharSequenceCompiler() {
    }
}
