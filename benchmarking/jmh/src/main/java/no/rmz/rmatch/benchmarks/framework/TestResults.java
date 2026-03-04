package no.rmz.rmatch.benchmarks.framework;

import java.util.concurrent.TimeUnit;

/**
 * Results from a benchmark test execution with performance metrics.
 *
 * <p>This class captures comprehensive performance data from JMH benchmark runs, including timing,
 * memory usage, and pattern matching statistics.
 */
public final class TestResults {
  private final String testName;
  private final long durationNanos;
  private final long memoryUsedBytes;
  private final int matchCount;
  private final int patternCount;
  private final long timestamp;
  private final double throughputOpsPerSec;

  /**
   * Creates test results.
   *
   * @param testName Name of the test
   * @param durationNanos Test duration in nanoseconds
   * @param memoryUsedBytes Memory used in bytes
   * @param matchCount Number of matches found
   * @param patternCount Number of patterns tested
   * @param throughputOpsPerSec Operations per second throughput
   */
  public TestResults(
      final String testName,
      final long durationNanos,
      final long memoryUsedBytes,
      final int matchCount,
      final int patternCount,
      final double throughputOpsPerSec) {
    this.testName = testName;
    this.durationNanos = durationNanos;
    this.memoryUsedBytes = memoryUsedBytes;
    this.matchCount = matchCount;
    this.patternCount = patternCount;
    this.timestamp = System.currentTimeMillis();
    this.throughputOpsPerSec = throughputOpsPerSec;
  }

  public String getTestName() {
    return testName;
  }

  public long getDurationNanos() {
    return durationNanos;
  }

  public long getDurationMillis() {
    return TimeUnit.NANOSECONDS.toMillis(durationNanos);
  }

  public long getMemoryUsedBytes() {
    return memoryUsedBytes;
  }

  public long getMemoryUsedMB() {
    return memoryUsedBytes / (1024 * 1024);
  }

  public int getMatchCount() {
    return matchCount;
  }

  public int getPatternCount() {
    return patternCount;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public double getThroughputOpsPerSec() {
    return throughputOpsPerSec;
  }

  /**
   * Formats results as structured log message compatible with modern monitoring systems.
   *
   * @return Formatted log message
   */
  public String toStructuredLog() {
    return String.format(
        "BENCHMARK_RESULTS: test=%s, duration_ms=%d, memory_mb=%d, matches=%d, patterns=%d, throughput_ops_sec=%.2f, timestamp=%d",
        testName,
        getDurationMillis(),
        getMemoryUsedMB(),
        matchCount,
        patternCount,
        throughputOpsPerSec,
        timestamp);
  }

  @Override
  public String toString() {
    return toStructuredLog();
  }
}
