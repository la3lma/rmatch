package no.rmz.rmatch.benchmarks;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Architecture-aware benchmarks that include system normalization for cross-platform comparison.
 * 
 * <p>This benchmark provides basic CPU normalization benchmarks for cross-architecture comparison:
 * <ul>
 *   <li>CPU normalization benchmarks</li>
 *   <li>Memory allocation benchmarks</li>
 *   <li>String processing benchmarks</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(
    value = 1,
    jvmArgs = {
      "-Xms512M", "-Xmx512M",
      // Java 25 JIT optimizations from performance analysis
      "-XX:+TieredCompilation",
      "-XX:CompileThreshold=500",
      // GC optimizations  
      "-XX:+UseCompactObjectHeaders"
    })
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
public class ArchitectureAwareBench {

  @Setup(Level.Trial)
  public void setup() {
    // Log basic system information for reproducibility
    System.out.println("=== Architecture Information ===");
    System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
    System.out.println("Architecture: " + System.getProperty("os.arch"));
    System.out.println("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
    System.out.println("Available Processors: " + Runtime.getRuntime().availableProcessors());
    System.out.println("Max Memory: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + "MB");
    System.out.println("=====================================");
  }

  /**
   * CPU normalization benchmark for cross-architecture comparison.
   * This provides the baseline score used to normalize performance across different systems.
   */
  @Benchmark
  public void cpuNormalizationBenchmark(Blackhole bh) {
    // Simple CPU-bound computation for normalization
    long sum = 0;
    for (int i = 0; i < 1000; i++) {
      sum += Math.sqrt(i) * Math.sin(i) + Math.cos(i * 0.5);
    }
    bh.consume(sum);
  }
  
  /**
   * Memory allocation benchmark for memory subsystem characterization.
   */
  @Benchmark
  public void memoryAllocationBenchmark(Blackhole bh) {
    // Allocate and access memory to test memory subsystem
    int[] array = new int[1000];
    for (int i = 0; i < array.length; i++) {
      array[i] = i * 2;
    }
    
    int sum = 0;
    for (int value : array) {
      sum += value;
    }
    bh.consume(sum);
  }
  
  /**
   * String processing benchmark for JVM string optimization characterization.
   */
  @Benchmark 
  public void stringProcessingBenchmark(Blackhole bh) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      sb.append("test").append(i).append(" ");
    }
    String result = sb.toString();
    bh.consume(result.toLowerCase().contains("test50"));
  }
}