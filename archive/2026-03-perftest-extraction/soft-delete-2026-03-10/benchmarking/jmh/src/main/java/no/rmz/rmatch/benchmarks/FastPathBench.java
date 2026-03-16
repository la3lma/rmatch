package no.rmz.rmatch.benchmarks;

import java.util.concurrent.TimeUnit;
import no.rmz.rmatch.engine.fastpath.AsciiOptimizer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmark for fast-path optimizations.
 *
 * <p>This benchmark tests the performance of ASCII fast-path optimizations compared to standard
 * Character methods.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(
    value = 1,
    jvmArgs = {"-Xms1G", "-Xmx1G"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
public class FastPathBench {

  @Param({"pure_ascii", "mixed", "pure_unicode"})
  public String textType;

  private String testText;

  @Setup
  public void setup() {
    switch (textType) {
      case "pure_ascii":
        // Typical English text with ASCII only
        testText = generateAsciiText(1000);
        break;
      case "mixed":
        // Mix of ASCII and Unicode characters
        testText = generateMixedText(1000);
        break;
      case "pure_unicode":
        // Mostly Unicode characters
        testText = generateUnicodeText(1000);
        break;
      default:
        testText = generateAsciiText(1000);
    }
  }

  private String generateAsciiText(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c = (char) ('a' + (i % 26));
      sb.append(c);
      if (i % 10 == 9) sb.append(' ');
      if (i % 50 == 49) sb.append('\n');
    }
    return sb.toString();
  }

  private String generateMixedText(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      if (i % 10 == 0) {
        // Add a Unicode character every 10 chars
        sb.append((char) (0x00A0 + (i % 100)));
      } else {
        sb.append((char) ('a' + (i % 26)));
      }
      if (i % 10 == 9) sb.append(' ');
    }
    return sb.toString();
  }

  private String generateUnicodeText(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      // Use various Unicode ranges
      sb.append((char) (0x00A0 + (i % 500)));
      if (i % 10 == 9) sb.append(' ');
    }
    return sb.toString();
  }

  @Benchmark
  public void asciiOptimizerIsLetterOrDigit(Blackhole bh) {
    for (int i = 0; i < testText.length(); i++) {
      bh.consume(AsciiOptimizer.isLetterOrDigit(testText.charAt(i)));
    }
  }

  @Benchmark
  public void standardIsLetterOrDigit(Blackhole bh) {
    for (int i = 0; i < testText.length(); i++) {
      bh.consume(Character.isLetterOrDigit(testText.charAt(i)));
    }
  }

  @Benchmark
  public void asciiOptimizerIsLetter(Blackhole bh) {
    for (int i = 0; i < testText.length(); i++) {
      bh.consume(AsciiOptimizer.isLetter(testText.charAt(i)));
    }
  }

  @Benchmark
  public void standardIsLetter(Blackhole bh) {
    for (int i = 0; i < testText.length(); i++) {
      bh.consume(Character.isLetter(testText.charAt(i)));
    }
  }

  @Benchmark
  public void asciiOptimizerIsWhitespace(Blackhole bh) {
    for (int i = 0; i < testText.length(); i++) {
      bh.consume(AsciiOptimizer.isWhitespace(testText.charAt(i)));
    }
  }

  @Benchmark
  public void standardIsWhitespace(Blackhole bh) {
    for (int i = 0; i < testText.length(); i++) {
      bh.consume(Character.isWhitespace(testText.charAt(i)));
    }
  }

  @Benchmark
  public void asciiFindFirstNonAscii(Blackhole bh) {
    bh.consume(AsciiOptimizer.findFirstNonAscii(testText, 0));
  }

  @Benchmark
  public void asciiIsAllAscii(Blackhole bh) {
    bh.consume(AsciiOptimizer.isAllAscii(testText));
  }
}
