package no.rmz.rmatch.performancetests;

import java.util.List;
import java.util.logging.Logger;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;

/**
 * Main class for running GitHub Action performance tests from command line. This orchestrates the
 * full performance comparison and generates results for CI.
 */
public final class GitHubActionPerformanceTestRunner {

  private static final Logger LOG =
      Logger.getLogger(GitHubActionPerformanceTestRunner.class.getName());

  public static void main(String[] args) {
    try {
      // Parse command line arguments
      int maxRegexps = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
      int numRuns = args.length > 1 ? Integer.parseInt(args[1]) : 5;

      LOG.info("Starting GitHub Action Performance Test");
      LOG.info("Max regexps: " + maxRegexps + ", Number of runs: " + numRuns);

      // Load baseline results
      List<MatcherBenchmarker.TestRunResult> baselineResults = BaselineManager.loadRmatchBaseline();

      // Run performance comparison
      GitHubActionPerformanceTest.ComparisonResult result =
          GitHubActionPerformanceTest.runComparison(maxRegexps, baselineResults);

      // Generate detailed performance report
      generatePerformanceReport(result);

      // Generate summary for PR comment
      generatePRSummary(result);

      // Update baseline if this is a merge to main (detected via environment)
      String gitRef = System.getenv("GITHUB_REF");
      if (gitRef != null && (gitRef.endsWith("/main") || gitRef.endsWith("/master"))) {
        LOG.info("Updating baseline for main branch");
        BaselineManager.saveRmatchBaseline("benchmarks/baseline", result.getRmatchResults());
        BaselineManager.saveJavaBaseline("benchmarks/baseline", result.getJavaResults());
      }

      // Exit with appropriate code based on performance result
      PerformanceCriteriaEvaluator.Status status = result.getPerformanceResult().getStatus();
      switch (status) {
        case PASS:
          System.out.println("✅ Performance check PASSED");
          System.exit(0);
          break;
        case WARNING:
          System.out.println("⚠️ Performance check WARNING");
          System.exit(2);
          break;
        case FAIL:
          System.out.println("❌ Performance check FAILED");
          System.exit(1);
          break;
        default:
          System.exit(1);
      }

    } catch (Exception e) {
      LOG.severe("Performance test failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void generatePerformanceReport(
      GitHubActionPerformanceTest.ComparisonResult result) {
    try {
      // Create detailed JSON report for artifacts
      StringBuilder jsonReport = new StringBuilder();
      jsonReport.append("{\n");
      jsonReport
          .append("  \"timestamp\": \"")
          .append(java.time.Instant.now().toString())
          .append("\",\n");
      jsonReport.append("  \"performance_result\": {\n");
      jsonReport
          .append("    \"status\": \"")
          .append(result.getPerformanceResult().getStatus().name())
          .append("\",\n");
      jsonReport
          .append("    \"explanation\": \"")
          .append(result.getPerformanceResult().getExplanation())
          .append("\",\n");
      jsonReport
          .append("    \"time_improvement_percent\": ")
          .append(result.getPerformanceResult().getTimeImprovementPercent())
          .append(",\n");
      jsonReport
          .append("    \"memory_improvement_percent\": ")
          .append(result.getPerformanceResult().getMemoryImprovementPercent())
          .append(",\n");
      jsonReport
          .append("    \"statistically_significant\": ")
          .append(result.getPerformanceResult().isStatisticallySignificant())
          .append("\n");
      jsonReport.append("  },\n");

      // Add current results summary
      jsonReport.append("  \"current_results\": {\n");
      jsonReport.append("    \"rmatch\": {\n");
      appendResultsStats(jsonReport, result.getRmatchResults(), "      ");
      jsonReport.append("    },\n");
      jsonReport.append("    \"java\": {\n");
      appendResultsStats(jsonReport, result.getJavaResults(), "      ");
      jsonReport.append("    }\n");
      jsonReport.append("  }\n");
      jsonReport.append("}\n");

      // Write to results directory
      java.nio.file.Files.createDirectories(java.nio.file.Paths.get("benchmarks/results"));
      String timestamp = java.time.Instant.now().toString().replaceAll("[:.]+", "-");
      String filename = "benchmarks/results/performance-check-" + timestamp + ".json";
      java.nio.file.Files.writeString(java.nio.file.Paths.get(filename), jsonReport.toString());

      LOG.info("Generated performance report: " + filename);

    } catch (Exception e) {
      LOG.warning("Failed to generate performance report: " + e.getMessage());
    }
  }

  private static void appendResultsStats(
      StringBuilder sb, List<MatcherBenchmarker.TestRunResult> results, String indent) {
    if (results.isEmpty()) {
      sb.append(indent).append("\"count\": 0\n");
      return;
    }

    double avgTime = results.stream().mapToLong(r -> r.durationInMillis()).average().orElse(0);
    double avgMemory = results.stream().mapToLong(r -> r.usedMemoryInMb()).average().orElse(0);

    sb.append(indent).append("\"count\": ").append(results.size()).append(",\n");
    sb.append(indent).append("\"avg_time_ms\": ").append(avgTime).append(",\n");
    sb.append(indent).append("\"avg_memory_mb\": ").append(avgMemory).append("\n");
  }

  private static void generatePRSummary(GitHubActionPerformanceTest.ComparisonResult result) {
    try {
      StringBuilder markdown = new StringBuilder();

      // Header with status icon
      PerformanceCriteriaEvaluator.PerformanceResult perfResult = result.getPerformanceResult();
      markdown
          .append("## ")
          .append(perfResult.getStatus().getDisplayName())
          .append(" Performance Comparison\n\n");

      markdown.append("**Result**: ").append(perfResult.getExplanation()).append("\n\n");

      // Performance metrics table
      markdown.append("### 📊 Performance Metrics\n\n");
      markdown.append("| Metric | Current (rmatch) | Baseline | Δ |\n");
      markdown.append("|--------|------------------|----------|---|\n");

      if (!result.getRmatchResults().isEmpty()) {
        double currentAvgTime =
            result.getRmatchResults().stream()
                .mapToLong(r -> r.durationInMillis())
                .average()
                .orElse(0);
        double currentAvgMemory =
            result.getRmatchResults().stream()
                .mapToLong(r -> r.usedMemoryInMb())
                .average()
                .orElse(0);

        String timeImprovement =
            String.format("%.1f%%", perfResult.getTimeImprovementPercent() * 100);
        String memoryImprovement =
            String.format("%.1f%%", perfResult.getMemoryImprovementPercent() * 100);

        markdown
            .append("| **Execution Time** | ")
            .append(String.format("%.0f ms", currentAvgTime))
            .append(" | Baseline | ")
            .append(timeImprovement)
            .append(" |\n");
        markdown
            .append("| **Memory Usage** | ")
            .append(String.format("%.0f MB", currentAvgMemory))
            .append(" | Baseline | ")
            .append(memoryImprovement)
            .append(" |\n");
        markdown
            .append("| **Statistical Significance** | ")
            .append(perfResult.isStatisticallySignificant() ? "✅ Yes" : "⚠️ Low")
            .append(" | - | - |\n");
      }

      markdown.append("\n### 🔬 Test Configuration\n");
      markdown
          .append("- **Runs**: ")
          .append(result.getRmatchResults().size())
          .append(" iterations\n");
      markdown.append("- **Environment**: GitHub Actions (ubuntu-latest)\n");
      markdown
          .append("- **Java Version**: ")
          .append(System.getProperty("java.version"))
          .append("\n");

      if (result.getBaselineRmatchResults().isEmpty()) {
        markdown.append(
            "\n> ⚠️ **Note**: No baseline data available. This will establish the first baseline.\n");
      }

      markdown.append("\n---\n");
      markdown.append(
          "*Performance check powered by [rmatch automated testing](PRD_PERFORMANCE_GITHUB_ACTION.md)*\n");

      // Write summary for PR comment script
      String filename = "benchmarks/results/pr-performance-summary.md";
      java.nio.file.Files.writeString(java.nio.file.Paths.get(filename), markdown.toString());

      LOG.info("Generated PR summary: " + filename);

    } catch (Exception e) {
      LOG.warning("Failed to generate PR summary: " + e.getMessage());
    }
  }
}
