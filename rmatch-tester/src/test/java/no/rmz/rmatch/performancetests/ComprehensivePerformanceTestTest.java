package no.rmz.rmatch.performancetests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for the ComprehensivePerformanceTest class.
 */
class ComprehensivePerformanceTestTest {

  @Test
  void testCreateDefaultConfig() {
    ComprehensivePerformanceTest.ComprehensiveTestConfig config =
        ComprehensivePerformanceTest.createDefaultConfig();
    
    assertNotNull(config);
    assertEquals("rmatch-tester/corpus/wuthr10.txt", config.getCorpusPath());
    assertEquals("rmatch-tester/corpus/real-words-in-wuthering-heights.txt", config.getRegexpPath());
    assertEquals(5000, config.getMaxRegexps());
    assertEquals(3, config.getNumRuns());
  }

  @Test
  void testCreateMaxScaleConfig() {
    ComprehensivePerformanceTest.ComprehensiveTestConfig config =
        ComprehensivePerformanceTest.createMaxScaleConfig();
    
    assertNotNull(config);
    assertEquals("rmatch-tester/corpus/wuthr10.txt", config.getCorpusPath());
    assertEquals("rmatch-tester/corpus/real-words-in-wuthering-heights.txt", config.getRegexpPath());
    assertEquals(10000, config.getMaxRegexps());
    assertEquals(3, config.getNumRuns());
  }

  @Test
  void testCustomConfig() {
    ComprehensivePerformanceTest.ComprehensiveTestConfig config =
        new ComprehensivePerformanceTest.ComprehensiveTestConfig(
            "test-corpus.txt", "test-regexps.txt", 100, 3);
    
    assertEquals("test-corpus.txt", config.getCorpusPath());
    assertEquals("test-regexps.txt", config.getRegexpPath());
    assertEquals(100, config.getMaxRegexps());
    assertEquals(3, config.getNumRuns());
  }

  @Test
  void testConfigWithDefaults() {
    // Test that negative values get replaced with defaults
    ComprehensivePerformanceTest.ComprehensiveTestConfig config =
        new ComprehensivePerformanceTest.ComprehensiveTestConfig(
            "test-corpus.txt", "test-regexps.txt", -1, 0);
    
    assertEquals(5000, config.getMaxRegexps()); // Should use default
    assertEquals(3, config.getNumRuns()); // Should use default
  }

  @Test
  @Timeout(120) // 2 minute timeout for integration test
  void testSmallScaleComprehensiveTest() {
    // This is a small-scale integration test to verify the comprehensive test works
    // We use very small numbers to keep test time reasonable
    
    ComprehensivePerformanceTest.ComprehensiveTestConfig config =
        new ComprehensivePerformanceTest.ComprehensiveTestConfig(
            "corpus/wuthr10.txt", // Relative path from rmatch-tester
            "corpus/real-words-in-wuthering-heights.txt",
            50, // Very small number for testing
            3);
    
    List<MatcherBenchmarker.TestRunResult> baselineResults = new ArrayList<>();
    
    // Run the test
    ComprehensivePerformanceTest.ComprehensiveTestResult result = 
        ComprehensivePerformanceTest.runComprehensiveTest(config, baselineResults);
    
    // Verify results
    assertNotNull(result);
    assertNotNull(result.getComparisonResult());
    assertNotNull(result.getConfig());
    assertTrue(result.getTotalTestTime() > 0);
    assertTrue(result.getRmatchThroughputMBps() >= 0);
    assertTrue(result.getJavaThroughputMBps() >= 0);
    assertTrue(result.getPerformanceRatio() >= 0);
    
    // Verify we have results
    assertFalse(result.getComparisonResult().getRmatchResults().isEmpty());
    assertFalse(result.getComparisonResult().getJavaResults().isEmpty());
    
    System.out.println("Small-scale comprehensive test completed successfully:");
    System.out.printf("  rmatch: %.3f MB/s%n", result.getRmatchThroughputMBps());
    System.out.printf("  java: %.3f MB/s%n", result.getJavaThroughputMBps());
    System.out.printf("  ratio: %.2fx%n", result.getPerformanceRatio());
  }

  @Test
  void testConfigNullChecks() {
    // Test null checks in config constructor
    assertThrows(NullPointerException.class, () ->
        new ComprehensivePerformanceTest.ComprehensiveTestConfig(null, "test", 100, 3));
    
    assertThrows(NullPointerException.class, () ->
        new ComprehensivePerformanceTest.ComprehensiveTestConfig("test", null, 100, 3));
  }

  @Test
  void testRunComprehensiveTestNullChecks() {
    // Test null checks in runComprehensiveTest
    ComprehensivePerformanceTest.ComprehensiveTestConfig config =
        ComprehensivePerformanceTest.createDefaultConfig();
    
    assertThrows(NullPointerException.class, () ->
        ComprehensivePerformanceTest.runComprehensiveTest(null, new ArrayList<>()));
    
    assertThrows(NullPointerException.class, () ->
        ComprehensivePerformanceTest.runComprehensiveTest(config, null));
  }
}