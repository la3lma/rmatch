/**
 * Copyright 2026. Bjørn Remseth (rmz@rmz.no).
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

import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.RegexpFactory;

/** Test-scope access to matcher implementations that are not public production API. */
public final class TestMatchers {

  private TestMatchers() {}

  public static Matcher newSingleMatcher() {
    return new MatcherImpl();
  }

  public static Matcher newSingleMatcher(
      final NDFACompiler compiler, final RegexpFactory regexpFactory) {
    return new MatcherImpl(compiler, regexpFactory);
  }
}
