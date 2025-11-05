package no.rmz.rmatch.performancetests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;

/**
 * Manages performance baseline data for comparison with current test results. Handles loading,
 * storing, and updating baseline performance metrics.
 */
public final class BaselineManager {

  private static final Logger LOG = Logger.getLogger(BaselineManager.class.getName());

  /** Default baseline directory */
  public static final String DEFAULT_BASELINE_DIR = "benchmarks/baseline";

  /** Baseline file for rmatch results */
  private static final String RMATCH_BASELINE_FILE = "rmatch-baseline.json";

  /** Baseline file for java results */
  private static final String JAVA_BASELINE_FILE = "java-baseline.json";

  /** Baseline data structure */
  public static class BaselineData {
    private final String version = "1.0";
    private final String timestamp;
    private final EnvironmentInfo environment;
    private final List<MatcherBenchmarker.TestRunResult> results;

    public BaselineData(
        String timestamp,
        EnvironmentInfo environment,
        List<MatcherBenchmarker.TestRunResult> results) {
      this.timestamp = timestamp;
      this.environment = environment;
      this.results = results;
    }

    // Getters
    public String getVersion() {
      return version;
    }

    public String getTimestamp() {
      return timestamp;
    }

    public EnvironmentInfo getEnvironment() {
      return environment;
    }

    public List<MatcherBenchmarker.TestRunResult> getResults() {
      return results;
    }
  }

  /** Environment information for baseline context */
  public static class EnvironmentInfo {
    private final String javaVersion;
    private final String osName;
    private final String osVersion;
    private final String gitCommit;
    private final String gitBranch;
    private final String architectureId;
    private final double normalizationScore;

    public EnvironmentInfo(
        String javaVersion,
        String osName,
        String osVersion,
        String gitCommit,
        String gitBranch,
        String architectureId,
        double normalizationScore) {
      this.javaVersion = javaVersion;
      this.osName = osName;
      this.osVersion = osVersion;
      this.gitCommit = gitCommit;
      this.gitBranch = gitBranch;
      this.architectureId = architectureId;
      this.normalizationScore = normalizationScore;
    }

    // Getters
    public String getJavaVersion() {
      return javaVersion;
    }

    public String getOsName() {
      return osName;
    }

    public String getOsVersion() {
      return osVersion;
    }

    public String getGitCommit() {
      return gitCommit;
    }

    public String getGitBranch() {
      return gitBranch;
    }

    public String getArchitectureId() {
      return architectureId;
    }

    public double getNormalizationScore() {
      return normalizationScore;
    }

    /**
     * Checks if this environment is the same architecture as another environment.
     *
     * @param other Environment to compare with
     * @return true if architectures match
     */
    public boolean isSameArchitecture(EnvironmentInfo other) {
      return architectureId != null
          && other.architectureId != null
          && architectureId.equals(other.architectureId);
    }
  }

  /** Private constructor for utility class */
  private BaselineManager() {}

  /**
   * Load baseline results for rmatch.
   *
   * @param baselineDir Directory containing baseline files
   * @return List of baseline test results, empty if no baseline exists
   */
  public static List<MatcherBenchmarker.TestRunResult> loadRmatchBaseline(String baselineDir) {
    return loadBaseline(baselineDir, RMATCH_BASELINE_FILE);
  }

  /**
   * Load baseline results for java.
   *
   * @param baselineDir Directory containing baseline files
   * @return List of baseline test results, empty if no baseline exists
   */
  public static List<MatcherBenchmarker.TestRunResult> loadJavaBaseline(String baselineDir) {
    return loadBaseline(baselineDir, JAVA_BASELINE_FILE);
  }

  /**
   * Load baseline results with default directory.
   *
   * @return List of baseline test results, empty if no baseline exists
   */
  public static List<MatcherBenchmarker.TestRunResult> loadRmatchBaseline() {
    return loadRmatchBaseline(DEFAULT_BASELINE_DIR);
  }

  /**
   * Load baseline results with architecture validation, discarding baselines from unknown
   * architectures.
   *
   * @param baselineDir Directory containing baseline files
   * @return List of baseline test results, empty if no baseline exists or if baseline architecture
   *     is unknown
   */
  public static List<MatcherBenchmarker.TestRunResult> loadRmatchBaselineWithArchitectureCheck(
      String baselineDir) {
    // First check if baseline has unknown architecture
    EnvironmentInfo baselineEnv = loadBaselineEnvironment(baselineDir, "rmatch");
    if (baselineEnv != null
        && "unknown".equals(baselineEnv.getArchitectureId())) {
      LOG.info(
          "Discarding baseline from unknown architecture - will establish new baseline from current run");
      return new ArrayList<>();
    }

    return loadRmatchBaseline(baselineDir);
  }

  /**
   * Load baseline results with architecture validation using default directory.
   *
   * @return List of baseline test results, empty if no baseline exists or if baseline architecture
   *     is unknown
   */
  public static List<MatcherBenchmarker.TestRunResult> loadRmatchBaselineWithArchitectureCheck() {
    return loadRmatchBaselineWithArchitectureCheck(DEFAULT_BASELINE_DIR);
  }

  /**
   * Load environment information from baseline file.
   *
   * @param baselineDir Directory containing baseline files
   * @param matcherType Matcher type ("rmatch" or "java")
   * @return Environment info if available, null otherwise
   */
  public static EnvironmentInfo loadBaselineEnvironment(String baselineDir, String matcherType) {
    String filename = "rmatch".equals(matcherType) ? RMATCH_BASELINE_FILE : JAVA_BASELINE_FILE;
    Path baselinePath = Paths.get(baselineDir, filename);

    if (!Files.exists(baselinePath)) {
      return null;
    }

    try {
      List<String> lines = Files.readAllLines(baselinePath);
      String javaVersion = "unknown";
      String osName = "unknown";
      String osVersion = "unknown";
      String gitCommit = "unknown";
      String gitBranch = "unknown";
      String architectureId = "unknown";
      double normalizationScore = 0.0;

      for (String line : lines) {
        if (line.startsWith("# Java: ")) {
          javaVersion = line.substring("# Java: ".length()).trim();
        } else if (line.startsWith("# OS: ")) {
          String osInfo = line.substring("# OS: ".length()).trim();
          String[] parts = osInfo.split(" ", 2);
          osName = parts.length > 0 ? parts[0] : "unknown";
          osVersion = parts.length > 1 ? parts[1] : "unknown";
        } else if (line.startsWith("# Git: ")) {
          String gitInfo = line.substring("# Git: ".length()).trim();
          int branchStart = gitInfo.indexOf('(');
          if (branchStart > 0) {
            gitCommit = gitInfo.substring(0, branchStart).trim();
            gitBranch = gitInfo.substring(branchStart + 1, gitInfo.indexOf(')')).trim();
          } else {
            gitCommit = gitInfo;
          }
        } else if (line.startsWith("# Architecture: ")) {
          architectureId = line.substring("# Architecture: ".length()).trim();
        } else if (line.startsWith("# Normalization Score: ")) {
          try {
            String scoreStr = line.substring("# Normalization Score: ".length()).trim();
            // Extract just the numeric part (handle cases with additional text)
            normalizationScore = Double.parseDouble(scoreStr.split("\\s+")[0]);
          } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            LOG.warning("Failed to parse normalization score from baseline");
          }
        }
      }

      return new EnvironmentInfo(
          javaVersion, osName, osVersion, gitCommit, gitBranch, architectureId, normalizationScore);

    } catch (IOException e) {
      LOG.warning(
          "Failed to load environment from baseline: "
              + baselinePath
              + ", error: "
              + e.getMessage());
      return null;
    }
  }

  /**
   * Save new baseline results for rmatch.
   *
   * @param baselineDir Directory to store baseline files
   * @param results Test results to save as new baseline
   */
  public static void saveRmatchBaseline(
      String baselineDir, List<MatcherBenchmarker.TestRunResult> results) {
    saveBaseline(baselineDir, RMATCH_BASELINE_FILE, results);
  }

  /**
   * Save new baseline results for java.
   *
   * @param baselineDir Directory to store baseline files
   * @param results Test results to save as new baseline
   */
  public static void saveJavaBaseline(
      String baselineDir, List<MatcherBenchmarker.TestRunResult> results) {
    saveBaseline(baselineDir, JAVA_BASELINE_FILE, results);
  }

  /**
   * Check if baseline exists.
   *
   * @param baselineDir Directory containing baseline files
   * @param matcherType Matcher type ("rmatch" or "java")
   * @return true if baseline exists
   */
  public static boolean baselineExists(String baselineDir, String matcherType) {
    String filename = "rmatch".equals(matcherType) ? RMATCH_BASELINE_FILE : JAVA_BASELINE_FILE;
    Path baselinePath = Paths.get(baselineDir, filename);
    return Files.exists(baselinePath);
  }

  private static List<MatcherBenchmarker.TestRunResult> loadBaseline(
      String baselineDir, String filename) {
    Path baselinePath = Paths.get(baselineDir, filename);

    if (!Files.exists(baselinePath)) {
      LOG.info("No baseline file found at: " + baselinePath);
      return new ArrayList<>();
    }

    try {
      List<String> lines = Files.readAllLines(baselinePath);
      List<MatcherBenchmarker.TestRunResult> results = new ArrayList<>();

      // Simple CSV-like format parsing: matcherTypeName,usedMemoryInMb,durationInMillis
      for (String line : lines) {
        if (line.startsWith("#") || line.trim().isEmpty()) {
          continue; // Skip comments and empty lines
        }

        String[] parts = line.split(",");
        if (parts.length >= 3) {
          try {
            String matcherTypeName = parts[0].trim();
            long usedMemoryInMb = Long.parseLong(parts[1].trim());
            long durationInMillis = Long.parseLong(parts[2].trim());

            // Create TestRunResult with empty loggedMatches for baseline
            MatcherBenchmarker.TestRunResult result =
                new MatcherBenchmarker.TestRunResult(
                    matcherTypeName, Collections.emptyList(), usedMemoryInMb, durationInMillis);
            results.add(result);
          } catch (NumberFormatException e) {
            LOG.warning("Invalid baseline data line: " + line);
          }
        }
      }

      LOG.info("Loaded " + results.size() + " baseline results from: " + baselinePath);
      return results;

    } catch (IOException e) {
      LOG.warning("Failed to load baseline from: " + baselinePath + ", error: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  private static void saveBaseline(
      String baselineDir, String filename, List<MatcherBenchmarker.TestRunResult> results) {
    try {
      // Ensure baseline directory exists
      Path baselineDirPath = Paths.get(baselineDir);
      Files.createDirectories(baselineDirPath);

      // Write to file in simple CSV format
      Path baselinePath = baselineDirPath.resolve(filename);

      try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(baselinePath))) {
        // Write header
        writer.println("# Baseline performance data");
        writer.println("# Generated: " + Instant.now().toString());
        EnvironmentInfo env = getCurrentEnvironment();
        writer.println("# Java: " + env.getJavaVersion());
        writer.println("# OS: " + env.getOsName() + " " + env.getOsVersion());
        writer.println("# Git: " + env.getGitCommit() + " (" + env.getGitBranch() + ")");
        writer.println("# Architecture: " + env.getArchitectureId());
        writer.println(
            "# Normalization Score: " + String.format("%.2f", env.getNormalizationScore()));
        writer.println("# Format: matcherTypeName,usedMemoryInMb,durationInMillis");
        writer.println();

        // Write data
        for (MatcherBenchmarker.TestRunResult result : results) {
          writer.printf(
              "%s,%d,%d%n",
              result.matcherTypeName(), result.usedMemoryInMb(), result.durationInMillis());
        }
      }

      LOG.info("Saved " + results.size() + " baseline results to: " + baselinePath);

    } catch (IOException e) {
      LOG.severe("Failed to save baseline: " + e.getMessage());
      throw new RuntimeException("Failed to save baseline", e);
    }
  }

  /**
   * Get current environment information including architecture details.
   *
   * @return Current environment information
   */
  public static EnvironmentInfo getCurrentEnvironment() {
    String javaVersion = System.getProperty("java.version", "unknown");
    String osName = System.getProperty("os.name", "unknown");
    String osVersion = System.getProperty("os.version", "unknown");

    // Try to get git info (may fail in some environments)
    String gitCommit = getGitInfo("rev-parse", "HEAD");
    String gitBranch = getGitInfo("rev-parse", "--abbrev-ref", "HEAD");

    // Collect architecture info and normalization score
    String architectureId = "unknown";
    double normalizationScore = 0.0;

    try {
      java.util.Map<String, Object> systemInfo =
          no.rmz.rmatch.performancetests.utils.SystemInfo.collectSystemInfo();
      architectureId =
          no.rmz.rmatch.performancetests.utils.SystemInfo.generateArchitectureId(systemInfo);

      // Run normalization benchmark (using 3 runs for balance between accuracy and speed)
      normalizationScore =
          no.rmz.rmatch.performancetests.utils.NormalizationBenchmark.runBenchmarkMedian(3);

      LOG.info("Architecture ID: " + architectureId);
      LOG.info(String.format("Normalization score: %.2f ops/ms", normalizationScore));

    } catch (Exception e) {
      LOG.warning("Failed to collect architecture info: " + e.getMessage());
    }

    return new EnvironmentInfo(
        javaVersion, osName, osVersion, gitCommit, gitBranch, architectureId, normalizationScore);
  }

  private static String getGitInfo(String... command) {
    try {
      List<String> fullCommand = new ArrayList<>();
      fullCommand.add("git");
      fullCommand.addAll(Arrays.asList(command));

      Process process = new ProcessBuilder(fullCommand).redirectErrorStream(true).start();

      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String result = reader.readLine();
        return result != null ? result.trim() : "unknown";
      }
    } catch (Exception e) {
      LOG.fine("Could not get git info: " + e.getMessage());
      return "unknown";
    }
  }
}
