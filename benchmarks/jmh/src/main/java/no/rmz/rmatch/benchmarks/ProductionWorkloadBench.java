package no.rmz.rmatch.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.StringBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Production-like benchmark for fast-path optimizations.
 *
 * <p>Tests matching performance with large pattern sets (5000-10000 regexes) against realistic text
 * corpora. This is the CRITICAL benchmark that determines whether optimizations are actually
 * effective per repository guidelines.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(
    value = 1,
    jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
public class ProductionWorkloadBench {

  /** Default corpus length for testing. */
  private static final int DEFAULT_CORPUS_LENGTH = 10000;

  @Param({"1000", "5000"})
  public int patternCount;

  private List<String> patterns;
  private String corpus;
  private Matcher defaultMatcher;
  private Matcher ahoPrefilterMatcher;
  private Matcher fastPathMatcher;

  @Setup(Level.Trial)
  public void setup() throws RegexpParserException {
    // Generate realistic patterns
    patterns = generateRealisticPatterns(patternCount);

    // Load or generate a realistic corpus
    corpus = generateRealisticCorpus(DEFAULT_CORPUS_LENGTH);

    // Setup matcher with default configuration
    System.setProperty("rmatch.prefilter", "disabled");
    System.setProperty("rmatch.engine", "default");
    defaultMatcher = MatcherFactory.newMatcher();
    CounterAction defaultAction = new CounterAction();
    for (String pattern : patterns) {
      defaultMatcher.add(pattern, defaultAction);
    }

    // Setup matcher with AhoCorasick prefilter
    System.setProperty("rmatch.prefilter", "aho");
    System.setProperty("rmatch.engine", "default");
    ahoPrefilterMatcher = MatcherFactory.newMatcher();
    CounterAction ahoAction = new CounterAction();
    for (String pattern : patterns) {
      ahoPrefilterMatcher.add(pattern, ahoAction);
    }

    // Setup matcher with FastPath engine
    System.setProperty("rmatch.prefilter", "aho");
    System.setProperty("rmatch.engine", "fastpath");
    fastPathMatcher = MatcherFactory.newMatcher();
    CounterAction fastPathAction = new CounterAction();
    for (String pattern : patterns) {
      fastPathMatcher.add(pattern, fastPathAction);
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() throws InterruptedException {
    if (defaultMatcher != null) {
      defaultMatcher.shutdown();
    }
    if (ahoPrefilterMatcher != null) {
      ahoPrefilterMatcher.shutdown();
    }
    if (fastPathMatcher != null) {
      fastPathMatcher.shutdown();
    }
  }

  /**
   * Generate realistic regex patterns similar to what would be seen in production:
   *
   * <ul>
   *   <li>Literal strings with wildcards
   *   <li>Email patterns
   *   <li>URL patterns
   *   <li>Phone number patterns
   *   <li>Common word patterns
   * </ul>
   */
  private List<String> generateRealisticPatterns(int count) {
    List<String> result = new ArrayList<>(count);

    // Pattern templates
    String[] templates = {
      "user%d@example\\.com",
      "http://www\\.site%d\\.com/.*",
      "error%d:.*",
      "\\d{3}-\\d{3}-%04d",
      "product_%d_.*",
      "session[A-Z0-9]{8}%d",
      "log_entry_%d.*",
      "transaction_[0-9]{6}%d",
      "customer_%d_order.*",
      "warning: code %d"
    };

    for (int i = 0; i < count; i++) {
      String template = templates[i % templates.length];
      result.add(String.format(template, i));
    }

    return result;
  }

  /**
   * Generate a realistic text corpus that might match some patterns. Mix of:
   *
   * <ul>
   *   <li>Log entries
   *   <li>Email addresses
   *   <li>URLs
   *   <li>Plain text
   *   <li>Numbers and IDs
   * </ul>
   */
  private String generateRealisticCorpus(int approximateLength) {
    StringBuilder sb = new StringBuilder(approximateLength);

    String[] sentences = {
      "The quick brown fox jumps over the lazy dog.",
      "Processing transaction for customer_1234_order with ID trans_567890.",
      "User user456@example.com logged in from IP 192.168.1.100.",
      "Error occurred: error123: connection timeout after 30 seconds.",
      "Visiting http://www.site789.com/products/item123 for details.",
      "Phone contact: 555-123-4567 or 555-987-6543.",
      "Session started with token sessionABCD12345678.",
      "Log entry: log_entry_999 at timestamp 2024-01-15T10:30:00Z.",
      "Product catalog updated: product_42_revision_5 now available.",
      "Warning: code 404 - resource not found on server."
    };

    int sentenceIndex = 0;
    while (sb.length() < approximateLength) {
      sb.append(sentences[sentenceIndex % sentences.length]);
      sb.append(" ");
      sentenceIndex++;
    }

    return sb.toString();
  }

  @Benchmark
  public void matchWithDefaultEngine(Blackhole bh) {
    StringBuffer buffer = new StringBuffer(corpus);
    defaultMatcher.match(buffer);
    bh.consume(buffer);
  }

  @Benchmark
  public void matchWithAhoPrefilter(Blackhole bh) {
    StringBuffer buffer = new StringBuffer(corpus);
    ahoPrefilterMatcher.match(buffer);
    bh.consume(buffer);
  }

  @Benchmark
  public void matchWithFastPathEngine(Blackhole bh) {
    StringBuffer buffer = new StringBuffer(corpus);
    fastPathMatcher.match(buffer);
    bh.consume(buffer);
  }
}
