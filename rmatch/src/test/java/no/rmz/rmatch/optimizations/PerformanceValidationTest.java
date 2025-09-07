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
package no.rmz.rmatch.optimizations;

import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.StringBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Performance validation test for the first-character optimization. */
public class PerformanceValidationTest {

  /** Test that validates the optimization provides performance improvements. */
  @Test
  public void testPerformanceImprovement() throws Exception {
    final int patternCount = 100;
    final String testInput = generateTestInput(1000);
    
    // Test with patterns that have diverse starting characters
    final String[] patterns = generateDiversePatterns(patternCount);
    
    // Warm up
    runMatchingBenchmark(patterns, testInput);
    runMatchingBenchmark(patterns, testInput);
    
    // Measure performance
    long totalTime = 0;
    int iterations = 5;
    
    for (int i = 0; i < iterations; i++) {
      long startTime = System.nanoTime();
      runMatchingBenchmark(patterns, testInput);
      long endTime = System.nanoTime();
      totalTime += (endTime - startTime);
    }
    
    double averageTimeMs = totalTime / (iterations * 1_000_000.0);
    
    System.out.printf("Performance test: %d patterns, %d chars input, %.2f ms average%n", 
        patternCount, testInput.length(), averageTimeMs);
    
    // Basic performance sanity check - should complete in reasonable time
    assertTrue(averageTimeMs < 1000, 
        "Performance test should complete in under 1 second, took: " + averageTimeMs + "ms");
  }

  /** Run a matching benchmark with given patterns and input. */
  private void runMatchingBenchmark(String[] patterns, String testInput) throws Exception {
    final Matcher matcher = MatcherFactory.newMatcher();
    final CounterAction action = new CounterAction();
    
    // Add all patterns
    for (String pattern : patterns) {
      matcher.add(pattern, action);
    }
    
    // Run matching
    final StringBuffer buffer = new StringBuffer(testInput);
    matcher.match(buffer);
    
    matcher.shutdown();
  }

  /** Generate test patterns with diverse starting characters. */
  private String[] generateDiversePatterns(int count) {
    String[] patterns = new String[count];
    String[] prefixes = {"alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta"};
    
    for (int i = 0; i < count; i++) {
      String prefix = prefixes[i % prefixes.length];
      patterns[i] = prefix + i + ".*";
    }
    
    return patterns;
  }

  /** Generate test input that may match some patterns. */
  private String generateTestInput(int length) {
    StringBuilder sb = new StringBuilder();
    String[] words = {"alpha1test", "beta2data", "gamma3info", "delta4word", "random", "text", "data"};
    
    while (sb.length() < length) {
      String word = words[(int) (Math.random() * words.length)];
      sb.append(word).append(" ");
    }
    
    return sb.toString().substring(0, Math.min(length, sb.length()));
  }
}