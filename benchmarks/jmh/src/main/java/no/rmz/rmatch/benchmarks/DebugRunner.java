package no.rmz.rmatch.benchmarks;

import org.openjdk.jmh.runner.options.TimeValue;

/** This class is used to run benchmarks in debug mode, typically from within an IDE. */
public class DebugRunner {
  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.runner.options.Options opt =
        new org.openjdk.jmh.runner.options.OptionsBuilder()
            .include(".*Bench.*")
            .forks(0) // <— no fork, hit breakpoints
            .warmupIterations(0)
            .measurementIterations(1)
            .warmupTime(TimeValue.seconds(1L))
            .measurementTime(TimeValue.seconds(1L))
            .build();
    new org.openjdk.jmh.runner.Runner(opt).run();
  }
}
