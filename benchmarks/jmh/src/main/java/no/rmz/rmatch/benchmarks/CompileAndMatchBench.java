package no.rmz.rmatch.benchmarks;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import org.openjdk.jmh.annotations.*;

/** Minimal example benchmarks. Fill with real hot paths as you identify them. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class CompileAndMatchBench {

  @Param({"10", "100", "1000"})
  public int patternCount;

  private List<String> patterns;
  private String haystack;

  @Setup
  public void setup() {
    // TODO: replace with a representative pattern set
    patterns = java.util.stream.IntStream.range(0, patternCount).mapToObj(i -> "a.*b" + i).toList();
    haystack = "aaa bbb ccc a---b999 end";
  }

  @Benchmark
  public Matcher buildMatcher() {
    Matcher m = MatcherFactory.newMatcher();
    for (String p : patterns) m.addPattern(p.getBytes(StandardCharsets.UTF_8));
    m.compile();
    return m; // Returned object is ignored by JMH but keeps work alive.
  }

  @Benchmark
  public int matchOnce() {
    Matcher m = MatcherFactory.newMatcher();
    for (String p : patterns) m.addPattern(p.getBytes(StandardCharsets.UTF_8));
    m.compile();
    // TODO: invoke the actual match API; using a hypothetical method here:
    return m.match(haystack.getBytes(StandardCharsets.UTF_8));
  }
}

        // Replace the fabricated addPattern(byte[]) and match(byte[]) calls with the real API used
        // in your codebase.
