package no.rmz.rmatch.performancetests;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;

/**
 * Manages performance baseline data for comparison with current test results. Handles loading,
 * storing, and updating baseline performance metrics.
 */
public final class BaselineManager {

  private static final Logger LOG = Logger.getLogger(BaselineManager.class.getName());

  /** Default baseline directory */
  private static final String DEFAULT_BASELINE_DIR = "benchmarks/baseline";

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

    public EnvironmentInfo(
        String javaVersion, String osName, String osVersion, String gitCommit, String gitBranch) {
      this.javaVersion = javaVersion;
      this.osName = osName;
      this.osVersion = osVersion;
      this.gitCommit = gitCommit;
      this.gitBranch = gitBranch;
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

  private static EnvironmentInfo getCurrentEnvironment() {
    String javaVersion = System.getProperty("java.version", "unknown");
    String osName = System.getProperty("os.name", "unknown");
    String osVersion = System.getProperty("os.version", "unknown");

    // Try to get git info (may fail in some environments)
    String gitCommit = getGitInfo("rev-parse", "HEAD");
    String gitBranch = getGitInfo("rev-parse", "--abbrev-ref", "HEAD");

    return new EnvironmentInfo(javaVersion, osName, osVersion, gitCommit, gitBranch);
  }

  private static String getGitInfo(String... command) {
    try {
      List<String> fullCommand = new ArrayList<>();
      fullCommand.add("git");
      fullCommand.addAll(Arrays.asList(command));

      Process process = new ProcessBuilder(fullCommand).redirectErrorStream(true).start();

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String result = reader.readLine();
        return result != null ? result.trim() : "unknown";
      }
    } catch (Exception e) {
      LOG.fine("Could not get git info: " + e.getMessage());
      return "unknown";
    }
  }
}
