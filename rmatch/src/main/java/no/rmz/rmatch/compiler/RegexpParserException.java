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

/**
 * Thrown when rmatch cannot parse a regular expression.
 *
 * <p>This usually means the pattern is malformed or uses syntax outside the currently supported
 * rmatch subset. The project README lists the supported constructs for each release line.
 */
public final class RegexpParserException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * Create an exception with a human-readable parser error.
   *
   * @param msg explanation of what went wrong
   */
  public RegexpParserException(final String msg) {
    super(msg);
  }

  /**
   * Wrap another exception that occurred while parsing.
   *
   * @param e underlying parse failure
   */
  public RegexpParserException(final Exception e) {
    super(e);
  }
}
