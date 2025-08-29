package no.rmz.rmatch.performancetests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Test baseline management functionality. */
public final class BaselineManagerTest {

  @Test
  public void testBaselineInitialization(@TempDir Path tempDir) throws Exception {
    // Test that we can save and load baseline data
    String baselineDir = tempDir.toString();

    // Create test results
    List<MatcherBenchmarker.TestRunResult> testResults = new ArrayList<>();
    testResults.add(
        new MatcherBenchmarker.TestRunResult("rmatch", Collections.emptyList(), 100, 500));
    testResults.add(
        new MatcherBenchmarker.TestRunResult("rmatch", Collections.emptyList(), 110, 450));

    // Initially no baseline should exist
    assertFalse(BaselineManager.baselineExists(baselineDir, "rmatch"));

    // Save baseline
    BaselineManager.saveRmatchBaseline(baselineDir, testResults);

    // Now baseline should exist
    assertTrue(BaselineManager.baselineExists(baselineDir, "rmatch"));

    // Load baseline and verify
    List<MatcherBenchmarker.TestRunResult> loaded =
        BaselineManager.loadRmatchBaseline(baselineDir);
    assertEquals(2, loaded.size());
    assertEquals("rmatch", loaded.get(0).matcherTypeName());
    assertEquals(100, loaded.get(0).usedMemoryInMb());
    assertEquals(500, loaded.get(0).durationInMillis());
  }

  @Test
  public void testEmptyBaselineHandling(@TempDir Path tempDir) {
    // Test loading from non-existent directory
    String baselineDir = tempDir.resolve("nonexistent").toString();

    List<MatcherBenchmarker.TestRunResult> results =
        BaselineManager.loadRmatchBaseline(baselineDir);
    assertTrue(results.isEmpty());
  }

  @Test
  public void testBaselineFileFormat(@TempDir Path tempDir) throws Exception {
    // Test that saved baseline files have correct format
    String baselineDir = tempDir.toString();

    List<MatcherBenchmarker.TestRunResult> testResults = new ArrayList<>();
    testResults.add(
        new MatcherBenchmarker.TestRunResult("rmatch", Collections.emptyList(), 200, 300));

    BaselineManager.saveRmatchBaseline(baselineDir, testResults);

    // Check file contents
    Path baselineFile = Paths.get(baselineDir, "rmatch-baseline.json");
    assertTrue(Files.exists(baselineFile));

    List<String> lines = Files.readAllLines(baselineFile);
    boolean foundDataLine = false;
    for (String line : lines) {
      if (line.equals("rmatch,200,300")) {
        foundDataLine = true;
        break;
      }
    }
    assertTrue(foundDataLine, "Should contain data line: rmatch,200,300");
  }
}