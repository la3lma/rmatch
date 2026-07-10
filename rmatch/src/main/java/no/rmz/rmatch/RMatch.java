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
package no.rmz.rmatch;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.utils.RegexStringBuffer;

/**
 * Convenience entry point for the supported rmatch public API.
 *
 * <p>Use this class when ordinary application code needs a matcher or a string-backed input buffer.
 * The implementation packages remain deliberately outside the JPMS export surface so that rmatch
 * can keep improving its compiler and engine internals without making those implementation details
 * part of the compatibility promise.
 *
 * <p>The current public buffer helpers are intentionally named {@code stringBuffer} because they
 * materialize input as a {@link String}. rmatch buffers are content-only and must support
 * offset-based character and substring lookup, so unbounded streams are not directly supported by
 * the facade; a bounded-window buffer implementation remains possible future work.
 */
public final class RMatch {

  /**
   * Create the recommended matcher for this runtime.
   *
   * <p>On machines with several available processors this may return a partitioned matcher, which
   * can invoke match actions concurrently. Use {@link #newSingleMatcher()} when deterministic
   * single-engine execution is more important than throughput.
   *
   * <p>The automatic choice is a hardware-based starting point, not a promise of optimal
   * performance for every workload. Use {@link #newMatcher(int)} to select the parallelism
   * explicitly.
   *
   * @return new matcher instance ready for pattern registration
   */
  public static Matcher newMatcher() {
    return MatcherFactory.newMatcher();
  }

  /**
   * Create a matcher with application-selected parallelism.
   *
   * <p>The value controls how many pattern partitions are created and, for values greater than one,
   * the maximum number of worker threads that scan those partitions concurrently. Every partition
   * scans the same input buffer with its share of the registered patterns. More parallelism can
   * therefore improve a sufficiently large many-pattern workload, but it also consumes more memory
   * and memory bandwidth and may make smaller workloads slower.
   *
   * <p>A value of one is equivalent to {@link #newSingleMatcher()} and does not create a worker
   * pool. Values through 1024 are supported so that large servers and workstations can use their
   * available hardware without an artificially low ceiling. Callers selecting very high values are
   * responsible for ensuring that the JVM and operating system can support the corresponding number
   * of platform threads.
   *
   * @param parallelism requested pattern partitions and maximum concurrent workers
   * @return new matcher instance ready for pattern registration
   * @throws IllegalArgumentException if {@code parallelism} is outside the range 1 through 1024
   */
  public static Matcher newMatcher(final int parallelism) {
    return MatcherFactory.newMatcher(parallelism);
  }

  /**
   * Create a single-engine matcher.
   *
   * <p>This is a good fit for small examples, deterministic debugging, and callers that do not want
   * action callbacks from multiple worker threads.
   *
   * @return new single-engine matcher instance
   */
  public static Matcher newSingleMatcher() {
    return MatcherFactory.newSingleMatcher();
  }

  /**
   * Adapt a {@link String} to the {@link Buffer} interface consumed by matchers.
   *
   * <p>The returned buffer is finite and fully materialized. If input starts as a file, reader, or
   * input stream, use the corresponding {@code stringBuffer} overload. Those overloads still read
   * the whole input into memory before matching. A lazy file-backed {@link Buffer} only needs to
   * provide positional character and substring lookup; a bounded-window buffer over streaming input
   * is likewise expressible but not currently shipped.
   *
   * @param text input text to scan
   * @return independent buffer over {@code text}
   */
  public static Buffer stringBuffer(final String text) {
    return new RegexStringBuffer(text);
  }

  /**
   * Adapt a character sequence by materializing it as a {@link String}.
   *
   * @param text input text to scan
   * @return independent buffer over {@code text.toString()}
   */
  public static Buffer stringBuffer(final CharSequence text) {
    return stringBuffer(text.toString());
  }

  /**
   * Read a finite text file completely and adapt it as a string-backed buffer.
   *
   * <p>This method is a convenience for callers, not a file-streaming implementation.
   *
   * @param path path to read
   * @param charset charset used to decode file bytes
   * @return independent buffer over the decoded file contents
   * @throws IOException if the file cannot be read
   */
  public static Buffer stringBuffer(final Path path, final Charset charset) throws IOException {
    return stringBuffer(Files.readString(path, charset));
  }

  /**
   * Read a finite character stream completely and adapt it as a string-backed buffer.
   *
   * <p>The reader is consumed but not closed.
   *
   * @param reader reader to consume
   * @return independent buffer over the consumed characters
   * @throws IOException if the reader cannot be read
   */
  public static Buffer stringBuffer(final Reader reader) throws IOException {
    final StringBuilder sb = new StringBuilder();
    final char[] chunk = new char[8192];
    int read;
    while ((read = reader.read(chunk)) != -1) {
      sb.append(chunk, 0, read);
    }
    return stringBuffer(sb.toString());
  }

  /**
   * Read a finite byte stream completely, decode it, and adapt it as a string-backed buffer.
   *
   * <p>The stream is consumed but not closed.
   *
   * @param stream stream to consume
   * @param charset charset used to decode stream bytes
   * @return independent buffer over the decoded stream contents
   * @throws IOException if the stream cannot be read
   */
  public static Buffer stringBuffer(final InputStream stream, final Charset charset)
      throws IOException {
    return stringBuffer(new String(stream.readAllBytes(), charset));
  }

  private RMatch() {}
}
