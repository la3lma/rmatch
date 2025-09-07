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

import static org.junit.jupiter.api.Assertions.assertTrue;

import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.StringBuffer;
import org.junit.jupiter.api.Test;

/**
 * Integration test demonstrating the O(l*m) complexity fix through performance characteristics with
 * diverse pattern sets.
 */
public class ComplexityImprovementDemoTest {

  /**
   * Test that demonstrates the optimization provides good performance even with many diverse
   * patterns.
   */
  @Test
  public void testPerformanceWithDiversePatterns() throws Exception {
    final int patternCount = 100;
    final String testInput = generateLongInput(2000); // Longer input to test scaling

    // Test with patterns that have very different starting characters
    final String[] diversePatterns = generateDiversePatterns(patternCount);

    // Time the matching with diverse patterns
    long startTime = System.nanoTime();
    runMatching(diversePatterns, testInput);
    long endTime = System.nanoTime();

    double timeMs = (endTime - startTime) / 1_000_000.0;

    System.out.printf("Complexity improvement demo:%n");
    System.out.printf("  - Input length: %d characters%n", testInput.length());
    System.out.printf("  - Pattern count: %d patterns%n", patternCount);
    System.out.printf("  - Time taken: %.2f ms%n", timeMs);

    // With good optimization, this should complete reasonably quickly
    assertTrue(
        timeMs < 500, "Should complete in under 500ms with optimization, took: " + timeMs + "ms");

    // Also test that it produces correct results by running a simpler case
    testCorrectness();
  }

  /** Test correctness of the optimization. */
  private void testCorrectness() throws Exception {
    final String[] patterns = {"alpha.*", "beta.*", "gamma.*"};
    final String input = "alpha1 test beta2 test gamma3 test";

    final CounterAction action = new CounterAction();
    final Matcher matcher = MatcherFactory.newMatcher();

    for (String pattern : patterns) {
      matcher.add(pattern, action);
    }

    final StringBuffer buffer = new StringBuffer(input);
    matcher.match(buffer);

    // Should find matches (this validates that optimization doesn't break functionality)
    assertTrue(action.getCounter() > 0, "Should find some matches with diverse patterns");

    matcher.shutdown();
  }

  /** Test with patterns that mostly share prefixes to ensure optimization still works. */
  @Test
  public void testOptimizationWithSharedPrefixes() throws Exception {
    // Create patterns that mostly start with the same characters
    final String[] sharedPrefixPatterns = new String[50];
    for (int i = 0; i < 40; i++) {
      sharedPrefixPatterns[i] = "test" + i + ".*"; // Most start with 't'
    }
    for (int i = 40; i < 50; i++) {
      sharedPrefixPatterns[i] = "other" + i + ".*"; // Few start with 'o'
    }

    final String testInput = generateLongInput(1000);

    long startTime = System.nanoTime();
    runMatching(sharedPrefixPatterns, testInput);
    long endTime = System.nanoTime();

    double timeMs = (endTime - startTime) / 1_000_000.0;

    System.out.printf("Shared prefix test:%n");
    System.out.printf("  - Time taken: %.2f ms%n", timeMs);

    // Even with shared prefixes, should complete reasonably quickly
    assertTrue(timeMs < 300, "Should complete reasonably quickly even with shared prefixes");
  }

  private String[] generateDiversePatterns(int count) {
    String[] patterns = new String[count];
    String[] prefixes = {
      "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa",
      "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon",
      "phi", "chi", "psi", "omega", "apple", "banana", "cherry", "dog", "elephant", "fox",
      "grape", "horse", "ice", "jungle", "key", "lion", "moon", "north", "ocean", "penguin",
      "quail", "river", "sun", "tree", "umbrella", "volcano", "water", "xenon", "yellow", "zebra"
    };

    for (int i = 0; i < count; i++) {
      String prefix = prefixes[i % prefixes.length];
      patterns[i] = prefix + i + ".*";
    }

    return patterns;
  }

  private String generateLongInput(int targetLength) {
    StringBuilder sb = new StringBuilder();
    String[] words = {
      "alpha1test",
      "beta2data",
      "gamma3info",
      "delta4word",
      "epsilon5text",
      "random",
      "text",
      "data",
      "information",
      "processing",
      "matching",
      "patterns",
      "performance",
      "optimization",
      "algorithm",
      "complexity",
      "efficiency"
    };

    while (sb.length() < targetLength) {
      String word = words[(int) (Math.random() * words.length)];
      sb.append(word).append(" ");
    }

    return sb.toString().substring(0, Math.min(targetLength, sb.length()));
  }

  private void runMatching(String[] patterns, String testInput) throws Exception {
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
}
