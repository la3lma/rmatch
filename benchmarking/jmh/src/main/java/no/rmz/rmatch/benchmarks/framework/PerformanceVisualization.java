package no.rmz.rmatch.benchmarks.framework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced performance visualization that combines JMH results with legacy CSV data for
 * comprehensive performance reporting and improved GitHub visualizations.
 *
 * <p>This class bridges the gap between the legacy CSV-based system and the modern JMH-based
 * framework, providing:
 *
 * <ul>
 *   <li>Unified data representation for both legacy and modern results
 *   <li>Enhanced visualization data suitable for charts and graphs
 *   <li>GitHub Actions compatible reporting
 *   <li>Architecture-aware performance comparison
 * </ul>
 */
public class PerformanceVisualization {

  private static final Logger LOG = Logger.getLogger(PerformanceVisualization.class.getName());

  /** Unified performance result that can represent both JMH and CSV data. */
  public static class UnifiedResult {
    private final String testName;
    private final long timestamp;
    private final double throughputOpsPerSec;
    private final long durationMs;
    private final long memoryMb;
    private final String architectureId;
    private final String source; // "JMH" or "CSV"
    private final String matcherType;

    public UnifiedResult(
        String testName,
        long timestamp,
        double throughputOpsPerSec,
        long durationMs,
        long memoryMb,
        String architectureId,
        String source,
        String matcherType) {
      this.testName = testName;
      this.timestamp = timestamp;
      this.throughputOpsPerSec = throughputOpsPerSec;
      this.durationMs = durationMs;
      this.memoryMb = memoryMb;
      this.architectureId = architectureId;
      this.source = source;
      this.matcherType = matcherType;
    }

    // Getters
    public String getTestName() {
      return testName;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public double getThroughputOpsPerSec() {
      return throughputOpsPerSec;
    }

    public long getDurationMs() {
      return durationMs;
    }

    public long getMemoryMb() {
      return memoryMb;
    }

    public String getArchitectureId() {
      return architectureId;
    }

    public String getSource() {
      return source;
    }

    public String getMatcherType() {
      return matcherType;
    }

    public String getFormattedTimestamp() {
      return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
          .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
  }

  /** Generate enhanced performance report suitable for GitHub Actions and visualization. */
  public static String generateEnhancedReport(List<UnifiedResult> results, String title) {
    StringBuilder report = new StringBuilder();

    report.append("# ").append(title).append("\n\n");
    report.append("## Performance Summary\n\n");

    // Group by matcher type for comparison
    var resultsByMatcher =
        results.stream().collect(Collectors.groupingBy(UnifiedResult::getMatcherType));

    report.append(
        "| Matcher Type | Avg Throughput (ops/sec) | Avg Duration (ms) | Avg Memory (MB) | Data Points |\n");
    report.append(
        "|--------------|--------------------------|-------------------|-----------------|-------------|\n");

    for (var entry : resultsByMatcher.entrySet()) {
      String matcherType = entry.getKey();
      List<UnifiedResult> matcherResults = entry.getValue();

      double avgThroughput =
          matcherResults.stream()
              .mapToDouble(UnifiedResult::getThroughputOpsPerSec)
              .average()
              .orElse(0.0);

      double avgDuration =
          matcherResults.stream().mapToLong(UnifiedResult::getDurationMs).average().orElse(0.0);

      double avgMemory =
          matcherResults.stream().mapToLong(UnifiedResult::getMemoryMb).average().orElse(0.0);

      report.append(
          String.format(
              "| %s | %.1f | %.0f | %.0f | %d |\n",
              matcherType, avgThroughput, avgDuration, avgMemory, matcherResults.size()));
    }

    report.append("\n## Architecture Information\n\n");

    // Show unique architectures in the dataset
    var architectures =
        results.stream()
            .map(UnifiedResult::getArchitectureId)
            .distinct()
            .collect(Collectors.toList());

    report.append("**Test Architectures**: ").append(String.join(", ", architectures)).append("\n");

    // Show data sources
    var sources =
        results.stream().map(UnifiedResult::getSource).distinct().collect(Collectors.toList());

    report.append("**Data Sources**: ").append(String.join(", ", sources)).append("\n\n");

    // Recent performance trend
    report.append("## Recent Performance Trend\n\n");

    var recentResults =
        results.stream()
            .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
            .limit(10)
            .collect(Collectors.toList());

    if (!recentResults.isEmpty()) {
      report.append("| Date | Test | Matcher | Throughput | Duration | Memory |\n");
      report.append("|------|------|---------|------------|----------|--------|\n");

      for (UnifiedResult result : recentResults) {
        report.append(
            String.format(
                "| %s | %s | %s | %.1f ops/sec | %d ms | %d MB |\n",
                result.getFormattedTimestamp(),
                result.getTestName(),
                result.getMatcherType(),
                result.getThroughputOpsPerSec(),
                result.getDurationMs(),
                result.getMemoryMb()));
      }
    }

    report.append("\n---\n");
    report.append("*Report generated by Enhanced rmatch Performance Visualization*\n");

    return report.toString();
  }

  /** Parse legacy CSV data and convert to unified format. */
  public static List<UnifiedResult> parseLegacyCsvData(Path csvFilePath, String testName) {
    List<UnifiedResult> results = new ArrayList<>();

    try {
      if (!Files.exists(csvFilePath)) {
        LOG.warning("CSV file not found: " + csvFilePath);
        return results;
      }

      List<String> lines = Files.readAllLines(csvFilePath);

      // Skip header line if present
      boolean skipFirst = !lines.isEmpty() && lines.get(0).contains("secondsSinceEpoch");

      for (int i = skipFirst ? 1 : 0; i < lines.size(); i++) {
        String line = lines.get(i).trim();
        if (line.isEmpty()) continue;

        try {
          String[] parts = line.split(",");
          if (parts.length >= 3) {
            long timestamp = Long.parseLong(parts[0].trim());
            long durationMs = Long.parseLong(parts[1].trim());
            long memoryMb = Long.parseLong(parts[2].trim());

            // Convert to throughput (approximate)
            double throughputOpsPerSec = durationMs > 0 ? 1000.0 / durationMs : 0.0;

            results.add(
                new UnifiedResult(
                    testName,
                    timestamp,
                    throughputOpsPerSec,
                    durationMs,
                    memoryMb,
                    "legacy", // Unknown architecture for old CSV data
                    "CSV",
                    "rmatch-legacy"));
          }
        } catch (NumberFormatException e) {
          LOG.warning("Invalid CSV line: " + line);
        }
      }

      LOG.info("Parsed " + results.size() + " results from CSV: " + csvFilePath);

    } catch (IOException e) {
      LOG.warning("Failed to read CSV file " + csvFilePath + ": " + e.getMessage());
    }

    return results;
  }

  /** Generate visualization data file suitable for external charting tools. */
  public static void generateVisualizationData(List<UnifiedResult> results, Path outputPath) {
    try {
      StringBuilder json = new StringBuilder();
      json.append("{\n");
      json.append("  \"generated\": \"").append(Instant.now().toString()).append("\",\n");
      json.append("  \"results\": [\n");

      for (int i = 0; i < results.size(); i++) {
        UnifiedResult result = results.get(i);
        if (i > 0) json.append(",\n");

        json.append("    {\n");
        json.append("      \"testName\": \"").append(result.getTestName()).append("\",\n");
        json.append("      \"timestamp\": ").append(result.getTimestamp()).append(",\n");
        json.append("      \"throughputOpsPerSec\": ")
            .append(result.getThroughputOpsPerSec())
            .append(",\n");
        json.append("      \"durationMs\": ").append(result.getDurationMs()).append(",\n");
        json.append("      \"memoryMb\": ").append(result.getMemoryMb()).append(",\n");
        json.append("      \"architectureId\": \"")
            .append(result.getArchitectureId())
            .append("\",\n");
        json.append("      \"source\": \"").append(result.getSource()).append("\",\n");
        json.append("      \"matcherType\": \"").append(result.getMatcherType()).append("\"\n");
        json.append("    }");
      }

      json.append("\n  ]\n");
      json.append("}\n");

      Files.write(outputPath, json.toString().getBytes());
      LOG.info("Generated visualization data: " + outputPath);

    } catch (IOException e) {
      LOG.warning("Failed to write visualization data: " + e.getMessage());
    }
  }
}
