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

import java.util.logging.Level;
import java.util.logging.Logger;
import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.RegexpStorage;

/**
 * An implementation of a compiler.
 */
public final class NDFACompilerImpl implements NDFACompiler {

    @Override
    public NDFANode compile(
            final Regexp regexp,
            final RegexpStorage rs) throws RegexpParserException {
        final ARegexpCompiler arc = new ARegexpCompiler(regexp);
        final SurfaceRegexpParser surfaceRegexpParser =
                new SurfaceRegexpParser(arc);
        surfaceRegexpParser.parse(regexp.getRexpString());
        return arc.getResult();
    }
}
