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

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.Map;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.RegexpStorage;

/**
 * A compiler that can be primed with predigested compilation results, enabling
 * it to compile known regular expressions into known NDFAnodes representing
 * them.
 */
public final class MockerCompiler implements NDFACompiler {

    /**
     * A map of mocked compilation results.
     */
    private final Map<Regexp, NDFANode> mockedCompilationResult;

    /**
     * Make a new instance, with a particular map for holding the results.
     * @param mockedCompilationResult the result that will used by
     *        this "compiler" to look up its
     *        result of compilation.
     */
    public MockerCompiler(final Map<Regexp, NDFANode> mockedCompilationResult) {
        this.mockedCompilationResult = checkNotNull(mockedCompilationResult);
    }

    @Override
    public NDFANode compile(
            final Regexp regexp,
            final RegexpStorage rs) {

        checkNotNull(regexp);
        checkNotNull(rs);

        if (mockedCompilationResult.containsKey(regexp)) {
            return mockedCompilationResult.get(regexp);
        } else {
            throw new IllegalArgumentException(
                    "Attempt to compile something that isn't already compiled");
        }
    }
}
