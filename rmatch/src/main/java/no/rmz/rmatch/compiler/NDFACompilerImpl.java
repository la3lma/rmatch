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
package no.rmz.rmatch.compiler;

import no.rmz.rmatch.RegexpParserException;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.RegexpStorage;

/** Production compiler from rmatch regular-expression state to NDFA nodes. */
public final class NDFACompilerImpl implements NDFACompiler {

  @Override
  public NDFANode compile(final Regexp regexp, final RegexpStorage rs)
      throws RegexpParserException {
    final ARegexpCompiler arc = new ARegexpCompiler(regexp);
    final SurfaceRegexpParser surfaceRegexpParser = new SurfaceRegexpParser(arc);
    surfaceRegexpParser.parse(regexp.getRexpString());
    return arc.getResult();
  }
}
