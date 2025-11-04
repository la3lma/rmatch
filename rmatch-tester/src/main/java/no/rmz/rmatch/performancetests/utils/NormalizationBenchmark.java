package no.rmz.rmatch.performancetests.utils;

import java.util.logging.Logger;

/**
 * Provides normalization benchmarks to compare performance across different hardware
 * configurations.
 *
 * <p>This benchmark runs a simple, reproducible computation that serves as a baseline for relative
 * performance measurement. The score can be used to normalize benchmark results when comparing
 * across different CPU architectures or machines.
 *
 * <p>The normalization benchmark is designed to be:
 *
 * <ul>
 *   <li>CPU-bound and compute-intensive
 *   <li>Deterministic and reproducible
 *   <li>Quick to execute (< 1 second)
 *   <li>Representative of general computational workload
 * </ul>
 */
public final class NormalizationBenchmark {

  private static final Logger LOG = Logger.getLogger(NormalizationBenchmark.class.getName());

  // Tuned to take approximately 100-500ms on typical hardware
  private static final int ITERATIONS = 1_000_000;
  private static final int WARMUP_ITERATIONS = 100_000;

  private NormalizationBenchmark() {
    // Utility class
  }

  /**
   * Runs the normalization benchmark and returns a score.
   *
   * <p>Higher scores indicate faster hardware. The score is operations per millisecond and is used
   * as a normalization factor when comparing benchmark results across different machines.
   *
   * @return Normalization score (operations per millisecond)
   */
  public static double runBenchmark() {
    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      computeWork(i);
    }

    // Force GC before measurement
    System.gc();
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Measure actual performance
    long startTime = System.nanoTime();
    long checksum = 0;

    for (int i = 0; i < ITERATIONS; i++) {
      checksum += computeWork(i);
    }

    long endTime = System.nanoTime();
    double durationMs = (endTime - startTime) / 1_000_000.0;

    // Use checksum to prevent JIT optimization from eliminating the computation
    // Note: We don't validate checksum value as it's only used to prevent optimization
    if (checksum == Long.MIN_VALUE) {
      // This should never happen; just prevents dead code elimination
      LOG.warning("Unexpected checksum value in normalization benchmark");
    }

    double score = ITERATIONS / durationMs; // operations per millisecond
    LOG.info(
        String.format(
            "Normalization benchmark completed: %.2f ops/ms (%.2f ms total)", score, durationMs));

    return score;
  }

  /**
   * Computes a mix of arithmetic operations representative of typical CPU workload.
   *
   * <p>This method combines integer arithmetic, bit operations, and simple branching to create a
   * balanced workload that stresses different CPU execution units.
   *
   * @param seed Input value for computation
   * @return Result of computation
   */
  private static long computeWork(int seed) {
    long result = seed;

    // Mix of operations to stress different CPU units
    result = (result * 31) + 17; // Multiply-add
    result ^= (result >>> 16); // Bit operations
    result = (result * 0x85ebca6b) + 0xc2b2ae35; // Hash-like mixing
    result ^= (result >>> 13);
    result = (result * 0xc2b2ae35) + 0x27d4eb2d;
    result ^= (result >>> 16);

    // Simple branch to prevent over-optimization
    if ((result & 0x1) == 0) {
      result += 1;
    }

    return result;
  }

  /**
   * Runs the normalization benchmark multiple times and returns the median score.
   *
   * <p>Running multiple iterations helps reduce noise from OS scheduling and other transient
   * effects.
   *
   * @param runs Number of benchmark runs (recommended: 3-5)
   * @return Median normalization score
   */
  public static double runBenchmarkMedian(int runs) {
    double[] scores = new double[runs];

    for (int i = 0; i < runs; i++) {
      scores[i] = runBenchmark();

      // Small pause between runs
      if (i < runs - 1) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    // Calculate median
    java.util.Arrays.sort(scores);
    double median;
    if (runs % 2 == 0) {
      median = (scores[runs / 2 - 1] + scores[runs / 2]) / 2.0;
    } else {
      median = scores[runs / 2];
    }

    LOG.info(
        String.format("Normalization benchmark median over %d runs: %.2f ops/ms", runs, median));
    return median;
  }

  /**
   * Computes a normalized score by dividing the raw benchmark result by the normalization factor.
   *
   * <p>This allows comparing results from different machines on a normalized scale.
   *
   * @param rawScore Raw benchmark score
   * @param normalizationFactor Machine-specific normalization factor
   * @return Normalized score
   */
  public static double normalizeScore(double rawScore, double normalizationFactor) {
    if (normalizationFactor == 0) {
      LOG.warning("Normalization factor is zero, returning raw score");
      return rawScore;
    }
    return rawScore / normalizationFactor;
  }

  /**
   * Main method for running the normalization benchmark standalone.
   *
   * @param args Command line arguments (unused)
   */
  public static void main(String[] args) {
    System.out.println("Running normalization benchmark...");
    System.out.println("=================================");

    double singleScore = runBenchmark();
    System.out.printf("Single run score: %.2f ops/ms\n\n", singleScore);

    double medianScore = runBenchmarkMedian(5);
    System.out.printf("Median score (5 runs): %.2f ops/ms\n", medianScore);

    System.out.println("\nThis score can be used as a normalization factor");
    System.out.println("when comparing benchmark results across different machines.");
  }
}
