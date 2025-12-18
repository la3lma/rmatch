package no.rmz.rmatch.performancetests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Exhaustively evaluates the key optimization switches using the full Wuthering Heights corpus and
 * 10,000 real-word regexps.
 */
class OptimizationSwitchExhaustiveTest {

  private static final String CORPUS_PATH = "corpus/wuthr10.txt";
  private static final String REGEXP_PATH = "corpus/real-words-in-wuthering-heights.txt";
  private static final int REGEXP_LIMIT = 10_000;

  private record OptimizationRunResult(
      String label, Map<String, String> properties, long durationMs, long usedMemoryMb) {}

  @Test
  @Timeout(value = 900)
  void exhaustiveOptimizationSwitchMatrixCompletes() {
    final List<String> regexps = MatcherBenchmarker.loadRegexpsFromFile(REGEXP_PATH, REGEXP_LIMIT);
    assertEquals(REGEXP_LIMIT, regexps.size(), "Should load the full 10K regexp set");

    final List<Boolean> toggleStates = List.of(Boolean.FALSE, Boolean.TRUE);
    final List<OptimizationRunResult> results = new ArrayList<>();

    int expectedCombinations = 0;
    for (boolean fastPathEnabled : toggleStates) {
      for (boolean bloomFilterEnabled : toggleStates) {
        if (fastPathEnabled && bloomFilterEnabled) {
          continue; // Mutually exclusive engines
        }
        for (boolean ahoPrefilterEnabled : toggleStates) {
          for (boolean aggressiveThreshold : toggleStates) {
            expectedCombinations++;
            Map<String, String> properties =
                buildPropertySet(
                    fastPathEnabled, bloomFilterEnabled, ahoPrefilterEnabled, aggressiveThreshold);

            String label = describe(properties);
            OptimizationRunResult result = runCombination(label, properties, regexps);
            results.add(result);
          }
        }
      }
    }

    assertEquals(expectedCombinations, results.size(), "All optimization combinations must run");
    assertFalse(results.isEmpty(), "At least one configuration must be exercised");

    // Emit a compact summary so CI logs capture the performance matrix.
    results.forEach(
        result ->
            System.out.printf(
                "%s => %d ms, %d MB%n",
                result.label(), result.durationMs(), result.usedMemoryMb()));
  }

  private Map<String, String> buildPropertySet(
      boolean fastPath, boolean bloomFilter, boolean ahoPrefilter, boolean aggressiveThreshold) {
    Map<String, String> props = new LinkedHashMap<>();
    if (fastPath) {
      props.put("rmatch.engine", "fastpath");
    } else if (bloomFilter) {
      props.put("rmatch.engine", "bloom");
    } else {
      props.put("rmatch.engine", "default");
    }

    props.put("rmatch.prefilter", ahoPrefilter ? "aho" : "none");
    props.put("rmatch.prefilter.threshold", aggressiveThreshold ? "5000" : "99999");
    return props;
  }

  private String describe(Map<String, String> props) {
    return String.format(
        "engine=%s,prefilter=%s,threshold=%s",
        props.get("rmatch.engine"), props.get("rmatch.prefilter"), props.get("rmatch.prefilter.threshold"));
  }

  private OptimizationRunResult runCombination(
      String label, Map<String, String> properties, List<String> regexps) {
    Map<String, String> previousValues = stashCurrentProperties(properties.keySet());
    properties.forEach(System::setProperty);
    try {
      Buffer buffer = new WutheringHeightsBuffer(CORPUS_PATH);
      Matcher matcher = MatcherFactory.newMatcher();
      MatcherBenchmarker.TestRunResult testResult =
          MatcherBenchmarker.testACorpusNG(label, matcher, regexps, buffer);

      return new OptimizationRunResult(
          label, properties, testResult.durationInMillis(), testResult.usedMemoryInMb());
    } finally {
      restoreProperties(previousValues);
    }
  }

  private Map<String, String> stashCurrentProperties(Iterable<String> keys) {
    Map<String, String> previousValues = new LinkedHashMap<>();
    for (String key : keys) {
      previousValues.put(key, System.getProperty(key));
    }
    return previousValues;
  }

  private void restoreProperties(Map<String, String> previousValues) {
    for (Map.Entry<String, String> entry : previousValues.entrySet()) {
      if (entry.getValue() == null) {
        System.getProperties().remove(entry.getKey());
      } else {
        System.setProperty(entry.getKey(), entry.getValue());
      }
    }
  }
}
