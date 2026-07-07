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
package no.rmz.rmatch.interfaces;

/**
 * Optional {@link Buffer} extension for buffers that can inspect the next character without
 * advancing.
 *
 * <p>Application code normally does not need to implement this interface. It is useful for custom
 * high-performance buffers because some engine optimizations can avoid speculative work when
 * lookahead is available. Implementations must preserve the cursor contract from {@link Buffer}:
 * {@link #peek()} must not change the value returned by {@link #getCurrentPos()} and must not
 * consume input.
 */
public interface LookaheadBuffer extends Buffer {

  /**
   * Return the character that would be returned by the next call to {@link #getNext()}.
   *
   * @return next character without advancing, or {@code null} when no further character is
   *     available
   */
  Character peek();
}
