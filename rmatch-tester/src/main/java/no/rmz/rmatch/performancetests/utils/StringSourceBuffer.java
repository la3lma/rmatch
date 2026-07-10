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
package no.rmz.rmatch.performancetests.utils;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import no.rmz.rmatch.Buffer;

/** An implementation of the Buffer interface, that holds all of the input as a String. */
public final class StringSourceBuffer implements Buffer {

  private final String str;

  public StringSourceBuffer(final String str) {
    this.str = checkNotNull(str);
  }

  @Override
  public boolean hasCharAt(final long pos) {
    return pos >= 0 && pos < str.length();
  }

  @Override
  public char charAt(final long pos) {
    return str.charAt(Math.toIntExact(pos));
  }

  @Override
  public String getString(final long start, final long stop) {
    return str.substring(Math.toIntExact(start), Math.toIntExact(stop));
  }

  public int getLength() {
    return str.length();
  }

  public String getString() {
    return str;
  }
}
