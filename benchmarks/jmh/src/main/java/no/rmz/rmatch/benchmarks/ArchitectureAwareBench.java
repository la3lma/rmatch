package no.rmz.rmatch.benchmarks;

import java.util.concurrent.TimeUnit;
import no.rmz.rmatch.performancetests.utils.SystemInfo;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Architecture-aware benchmarks that include system normalization for cross-platform comparison.
 * 
 * <p>This benchmark integrates with the rmatch architecture-aware system to provide:
 * <ul>
 *   <li>Cross-architecture performance normalization</li>
 *   <li>System information reporting for reproducibility</li>
 *   <li>Integration with existing baseline management</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.OPERATIONS)
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

  private SystemInfo systemInfo;
  
  @Setup(Level.Trial)
  public void setup() {
    systemInfo = new SystemInfo();
    
    // Log system information for reproducibility
    System.out.println("=== Architecture Information ===");
    System.out.println("Architecture ID: " + systemInfo.getArchitectureId());
    System.out.println("CPU Model: " + systemInfo.getCpuModel());
    System.out.println("CPU Cores: " + systemInfo.getCpuPhysicalCores() + " physical, " + 
                       systemInfo.getCpuLogicalCores() + " logical");
    System.out.println("OS: " + systemInfo.getOsName() + " " + systemInfo.getOsVersion());
    System.out.println("Java: " + systemInfo.getJavaVersion() + " (" + systemInfo.getJavaVendor() + ")");
    System.out.println("Memory: " + systemInfo.getMaxMemoryMb() + "MB max");
    System.out.println("Normalization Score: " + String.format("%.0f ops/ms", systemInfo.getNormalizationScore()));
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