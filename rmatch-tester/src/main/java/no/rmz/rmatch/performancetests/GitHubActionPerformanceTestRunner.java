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

      // Load baseline results with architecture check - discards baselines from unknown
      // architectures
      List<MatcherBenchmarker.TestRunResult> baselineResults =
          BaselineManager.loadRmatchBaselineWithArchitectureCheck();

      // Run performance comparison
      GitHubActionPerformanceTest.ComparisonResult result =
          GitHubActionPerformanceTest.runComparison(
              "rmatch-tester/corpus/wuthr10.txt",
              "rmatch-tester/corpus/real-words-in-wuthering-heights.txt",
              maxRegexps,
              numRuns,
              baselineResults);

      // Generate detailed performance report
      generatePerformanceReport(result);

      // Generate summary for PR comment
      generatePRSummary(result);

      // Update baseline if this is a merge to main (detected via environment) OR if no baseline
      // exists (bootstrap) OR if baseline was discarded due to unknown architecture
      String gitRef = System.getenv("GITHUB_REF");
      boolean isMainBranch =
          gitRef != null && (gitRef.endsWith("/main") || gitRef.endsWith("/master"));
      boolean isBootstrapCase = baselineResults.isEmpty();

      // Check if baseline was discarded due to unknown architecture
      boolean wasUnknownArchitectureBaseline = false;
      if (BaselineManager.baselineExists(BaselineManager.DEFAULT_BASELINE_DIR, "rmatch")) {
        BaselineManager.EnvironmentInfo existingBaselineEnv =
            BaselineManager.loadBaselineEnvironment(BaselineManager.DEFAULT_BASELINE_DIR, "rmatch");
        wasUnknownArchitectureBaseline =
            existingBaselineEnv != null
                && "unknown".equals(existingBaselineEnv.getArchitectureId())
                && baselineResults.isEmpty();
      }

      if (isMainBranch || isBootstrapCase || wasUnknownArchitectureBaseline) {
        if (isBootstrapCase && !wasUnknownArchitectureBaseline) {
          LOG.info("No baseline exists - establishing initial baseline from current results");
        } else if (wasUnknownArchitectureBaseline) {
          LOG.info(
              "Existing baseline has unknown architecture - establishing new baseline from current results");
        } else {
          LOG.info("Updating baseline for main branch");
        }
        BaselineManager.saveRmatchBaseline("benchmarks/baseline", result.getRmatchResults());
        BaselineManager.saveJavaBaseline("benchmarks/baseline", result.getJavaResults());
      }

      // Exit with appropriate code based on performance result
      PerformanceCriteriaEvaluator.Status status = result.getPerformanceResult().getStatus();
      PerformanceCriteriaEvaluator.PerformanceResult perfResult = result.getPerformanceResult();

      // Display detailed performance information for all cases
      System.out.println("\n=== Performance Check Results ===");
      System.out.println("Explanation: " + perfResult.getExplanation());
      System.out.printf("Time improvement: %.1f%%\n", perfResult.getTimeImprovementPercent() * 100);
      System.out.printf(
          "Memory improvement: %.1f%%\n", perfResult.getMemoryImprovementPercent() * 100);
      System.out.println(
          "Statistically significant: " + (perfResult.isStatisticallySignificant() ? "Yes" : "No"));

      if (!result.getRmatchResults().isEmpty()) {
        double avgTime =
            result.getRmatchResults().stream()
                .mapToLong(r -> r.durationInMillis())
                .average()
                .orElse(0);
        double avgMemory =
            result.getRmatchResults().stream()
                .mapToLong(r -> r.usedMemoryInMb())
                .average()
                .orElse(0);
        System.out.printf(
            "Current performance: %.0f ms, %.0f MB (avg of %d runs)\n",
            avgTime, avgMemory, result.getRmatchResults().size());
      }
      System.out.println("==================================\n");

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

      // Add architecture information section
      markdown.append("### 💻 Test Environment\n\n");
      markdown.append("| Attribute | Value |\n");
      markdown.append("|-----------|-------|\n");

      BaselineManager.EnvironmentInfo currentEnv = BaselineManager.getCurrentEnvironment();
      markdown
          .append("| **Architecture** | `")
          .append(currentEnv.getArchitectureId())
          .append("` |\n");

      if (currentEnv.getArchitectureId() != null
          && !currentEnv.getArchitectureId().equals("unknown")) {
        markdown
            .append("| **Normalization Score** | ")
            .append(String.format("%.0f ops/ms", currentEnv.getNormalizationScore()))
            .append(" |\n");
      }

      markdown.append("| **Java Version** | ").append(currentEnv.getJavaVersion()).append(" |\n");
      markdown
          .append("| **OS** | ")
          .append(currentEnv.getOsName())
          .append(" ")
          .append(currentEnv.getOsVersion())
          .append(" |\n");
      markdown
          .append("| **Test Runs** | ")
          .append(result.getRmatchResults().size())
          .append(" iterations |\n");

      // Check if baseline has different architecture
      BaselineManager.EnvironmentInfo baselineEnv =
          BaselineManager.loadBaselineEnvironment(BaselineManager.DEFAULT_BASELINE_DIR, "rmatch");
      if (baselineEnv != null && !currentEnv.isSameArchitecture(baselineEnv)) {
        markdown.append("\n> ⚠️ **Architecture Mismatch**: Baseline was run on `");
        markdown.append(baselineEnv.getArchitectureId());
        markdown.append("`. Performance normalization applied for fair comparison.\n");
      }

      markdown.append("\n");

      // Performance metrics table for rmatch
      markdown.append("### 📊 rmatch Performance Metrics\n\n");
      markdown.append("| Metric | Current | Baseline | Δ |\n");
      markdown.append("|--------|---------|----------|---|\n");

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

      // NEW: Native Java Matcher Performance Panel
      markdown.append("\n### 🔴 Native Java Matcher Performance\n\n");
      markdown.append("| Metric | Current Value |\n");
      markdown.append("|--------|---------------|\n");

      if (!result.getJavaResults().isEmpty()) {
        double javaAvgTime =
            result.getJavaResults().stream()
                .mapToLong(r -> r.durationInMillis())
                .average()
                .orElse(0);
        double javaAvgMemory =
            result.getJavaResults().stream().mapToLong(r -> r.usedMemoryInMb()).average().orElse(0);

        markdown
            .append("| **Execution Time** | ")
            .append(String.format("%.0f ms", javaAvgTime))
            .append(" |\n");
        markdown
            .append("| **Memory Usage** | ")
            .append(String.format("%.0f MB", javaAvgMemory))
            .append(" |\n");
        markdown
            .append("| **Test Runs** | ")
            .append(result.getJavaResults().size())
            .append(" iterations |\n");
      } else {
        markdown.append("| No Java performance data available | - |\n");
      }

      // NEW: Relative Performance Comparison Panel
      markdown.append("\n### ⚖️ Relative Performance (rmatch vs Java)\n\n");
      markdown.append("| Comparison | Ratio | Interpretation |\n");
      markdown.append("|------------|-------|----------------|\n");

      if (!result.getRmatchResults().isEmpty() && !result.getJavaResults().isEmpty()) {
        double rmatchAvgTime =
            result.getRmatchResults().stream()
                .mapToLong(r -> r.durationInMillis())
                .average()
                .orElse(0);
        double rmatchAvgMemory =
            result.getRmatchResults().stream()
                .mapToLong(r -> r.usedMemoryInMb())
                .average()
                .orElse(0);
        double javaAvgTime =
            result.getJavaResults().stream()
                .mapToLong(r -> r.durationInMillis())
                .average()
                .orElse(0);
        double javaAvgMemory =
            result.getJavaResults().stream().mapToLong(r -> r.usedMemoryInMb()).average().orElse(0);

        // Calculate ratios
        double timeRatio = javaAvgTime > 0 ? rmatchAvgTime / javaAvgTime : 0;
        double memoryRatio = javaAvgMemory > 0 ? rmatchAvgMemory / javaAvgMemory : 0;

        String timeInterpretation;
        String timeEmoji;
        if (timeRatio < 0.8) {
          timeInterpretation = "rmatch significantly faster";
          timeEmoji = "🟢";
        } else if (timeRatio < 1.2) {
          timeInterpretation = "comparable performance";
          timeEmoji = "🟡";
        } else {
          timeInterpretation = "Java regex faster";
          timeEmoji = "🔴";
        }

        String memoryInterpretation;
        String memoryEmoji;
        if (memoryRatio < 0.8) {
          memoryInterpretation = "rmatch uses less memory";
          memoryEmoji = "🟢";
        } else if (memoryRatio < 1.2) {
          memoryInterpretation = "comparable memory usage";
          memoryEmoji = "🟡";
        } else {
          memoryInterpretation = "Java regex uses less memory";
          memoryEmoji = "🔴";
        }

        markdown
            .append("| **Time Ratio (rmatch/java)** | ")
            .append(String.format("%.2fx", timeRatio))
            .append(" | ")
            .append(timeEmoji)
            .append(" ")
            .append(timeInterpretation)
            .append(" |\n");
        markdown
            .append("| **Memory Ratio (rmatch/java)** | ")
            .append(String.format("%.2fx", memoryRatio))
            .append(" | ")
            .append(memoryEmoji)
            .append(" ")
            .append(memoryInterpretation)
            .append(" |\n");

        // Add overall summary
        boolean timeWin = timeRatio < 1.0;
        boolean memoryWin = memoryRatio < 1.0;
        String overallSummary;
        if (timeWin && memoryWin) {
          overallSummary = "🏆 rmatch outperforms Java regex in both time and memory";
        } else if (timeWin || memoryWin) {
          overallSummary =
              "⚖️ Mixed performance: rmatch better in " + (timeWin ? "time" : "memory");
        } else {
          overallSummary = "❌ Java regex outperforms rmatch in both metrics";
        }
        markdown.append("| **Overall Assessment** | - | ").append(overallSummary).append(" |\n");
      } else {
        markdown.append("| No comparative data available | - | - |\n");
      }

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
