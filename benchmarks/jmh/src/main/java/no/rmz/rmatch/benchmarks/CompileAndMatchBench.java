package no.rmz.rmatch.benchmarks;

import java.util.concurrent.TimeUnit;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.CounterAction;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** JMH benchmark for rmatch compilation and matching performance. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(
    value = 1,
    jvmArgs = {"-Xms1G", "-Xmx1G"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class CompileAndMatchBench {

  @Param({"1", "10", "100", "1000"})
  public int patternCount;

  private String[] patterns;
  private String testInput;

  @Setup
  public void setup() {
    // Generate test patterns
    patterns = new String[patternCount];
    for (int i = 0; i < patternCount; i++) {
      patterns[i] = "pattern" + i + ".*";
    }

    // Create test input that will match some patterns
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      sb.append("pattern").append(i % patternCount).append("_test_data_");
    }
    testInput = sb.toString();
  }

  @Benchmark
  public void buildMatcher(Blackhole bh) {
    try {
      Matcher matcher = MatcherFactory.newMatcher();
      CounterAction action = new CounterAction();
      for (String pattern : patterns) {
        matcher.add(pattern, action);
      }
      bh.consume(matcher);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Benchmark
  public void matchOnce(Blackhole bh) {
    try {
      Matcher matcher = MatcherFactory.newMatcher();
      CounterAction action = new CounterAction();
      for (String pattern : patterns) {
        matcher.add(pattern, action);
      }

      // Convert string to buffer-like interface that rmatch expects
      no.rmz.rmatch.utils.StringBuffer buffer = new no.rmz.rmatch.utils.StringBuffer(testInput);

      matcher.match(buffer);
      bh.consume(buffer);

      matcher.shutdown();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
