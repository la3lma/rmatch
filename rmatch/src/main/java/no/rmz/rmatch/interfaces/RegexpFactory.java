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
package no.rmz.rmatch.interfaces;

import static com.google.common.base.Preconditions.checkNotNull;

import no.rmz.rmatch.impls.RegexpImpl;

/**
 * A producer of regexp instances. This is an useful interface to have when testing various things.
 * In production the default DEFAULT_REGEXP_FACTORY will be used all the time.
 */
public interface RegexpFactory {

  /**
   * Generate a new regular expression instance.
   *
   * @param regexpString A string that will be interpreted as a regular expression.
   * @return A Regexp instance.
   */
  Regexp newRegexp(final String regexpString);

  /**
   * The default regexp factory that is used in production. It works by making a RegexpImpl
   * instance.
   */
  RegexpFactory DEFAULT_REGEXP_FACTORY =
      (final String regexpString) -> {
        checkNotNull(regexpString, "regexpString can't be null");
        return new RegexpImpl(regexpString);
      };
}
