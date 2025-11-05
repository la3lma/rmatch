package no.rmz.rmatch.performancetests.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Collects detailed system and CPU information for architecture-aware benchmarking.
 *
 * <p>This utility captures CPU model, core counts, architecture, and other system details that
 * affect performance. This enables architecture-specific baseline comparisons and normalization of
 * results across different hardware.
 */
public final class SystemInfo {

  private static final Logger LOG = Logger.getLogger(SystemInfo.class.getName());

  private SystemInfo() {
    // Utility class
  }

  /**
   * Collects comprehensive system information including CPU, memory, and OS details.
   *
   * @return Map containing system information keys and values
   */
  public static Map<String, Object> collectSystemInfo() {
    Map<String, Object> info = new HashMap<>();

    // Basic Java system properties
    info.put("os_name", System.getProperty("os.name"));
    info.put("os_version", System.getProperty("os.version"));
    info.put("os_arch", System.getProperty("os.arch"));
    info.put("java_version", System.getProperty("java.version"));
    info.put("java_vendor", System.getProperty("java.vendor"));
    info.put("java_vm_name", System.getProperty("java.vm.name"));
    info.put("java_vm_version", System.getProperty("java.vm.version"));

    // Available processors
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    info.put("available_processors", availableProcessors);

    // Memory information
    Runtime runtime = Runtime.getRuntime();
    info.put("max_memory_mb", runtime.maxMemory() / (1024 * 1024));
    info.put("total_memory_mb", runtime.totalMemory() / (1024 * 1024));

    // Try to get more detailed CPU information based on OS
    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.contains("linux")) {
      collectLinuxCpuInfo(info);
    } else if (osName.contains("mac")) {
      collectMacCpuInfo(info);
    } else if (osName.contains("windows")) {
      collectWindowsCpuInfo(info);
    }

    // Get container/virtualization info if available
    detectContainerEnvironment(info);

    return info;
  }

  /**
   * Collects CPU information on Linux systems using /proc/cpuinfo and lscpu.
   *
   * @param info Map to populate with CPU information
   */
  private static void collectLinuxCpuInfo(Map<String, Object> info) {
    try {
      // Try lscpu first (more structured output)
      String lscpuOutput = executeCommand("lscpu");
      if (lscpuOutput != null && !lscpuOutput.isEmpty()) {
        parseLscpuOutput(lscpuOutput, info);
      }

      // Also try to get CPU model from /proc/cpuinfo
      String cpuInfoOutput = executeCommand("cat /proc/cpuinfo");
      if (cpuInfoOutput != null && !cpuInfoOutput.isEmpty()) {
        parseCpuInfo(cpuInfoOutput, info);
      }

      // Get CPU frequency info if available
      String freqInfo = executeCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
      if (freqInfo != null && !freqInfo.isEmpty()) {
        try {
          long freqKhz = Long.parseLong(freqInfo.trim());
          info.put("cpu_freq_mhz", freqKhz / 1000);
        } catch (NumberFormatException e) {
          // Ignore parse errors
        }
      }

    } catch (Exception e) {
      LOG.warning("Failed to collect Linux CPU info: " + e.getMessage());
    }
  }

  /**
   * Parses lscpu output to extract CPU information.
   *
   * @param output lscpu command output
   * @param info Map to populate with CPU information
   */
  private static void parseLscpuOutput(String output, Map<String, Object> info) {
    for (String line : output.split("\n")) {
      String[] parts = line.split(":", 2);
      if (parts.length == 2) {
        String key = parts[0].trim();
        String value = parts[1].trim();

        switch (key) {
          case "Architecture":
            info.put("cpu_architecture", value);
            break;
          case "Model name":
            info.put("cpu_model", value);
            break;
          case "CPU(s)":
            try {
              info.put("cpu_logical_cores", Integer.parseInt(value));
            } catch (NumberFormatException e) {
              // Ignore
            }
            break;
          case "Core(s) per socket":
            try {
              info.put("cpu_cores_per_socket", Integer.parseInt(value));
            } catch (NumberFormatException e) {
              // Ignore
            }
            break;
          case "Socket(s)":
            try {
              info.put("cpu_sockets", Integer.parseInt(value));
            } catch (NumberFormatException e) {
              // Ignore
            }
            break;
          case "Thread(s) per core":
            try {
              info.put("cpu_threads_per_core", Integer.parseInt(value));
            } catch (NumberFormatException e) {
              // Ignore
            }
            break;
          case "CPU MHz":
            try {
              info.put("cpu_mhz", Double.parseDouble(value));
            } catch (NumberFormatException e) {
              // Ignore
            }
            break;
          case "CPU max MHz":
            try {
              info.put("cpu_max_mhz", Double.parseDouble(value));
            } catch (NumberFormatException e) {
              // Ignore
            }
            break;
          case "L1d cache":
            info.put("cpu_l1d_cache", value);
            break;
          case "L1i cache":
            info.put("cpu_l1i_cache", value);
            break;
          case "L2 cache":
            info.put("cpu_l2_cache", value);
            break;
          case "L3 cache":
            info.put("cpu_l3_cache", value);
            break;
          default:
            // Ignore other fields
        }
      }
    }
  }

  /**
   * Parses /proc/cpuinfo to extract CPU model information.
   *
   * @param output /proc/cpuinfo content
   * @param info Map to populate with CPU information
   */
  private static void parseCpuInfo(String output, Map<String, Object> info) {
    for (String line : output.split("\n")) {
      if (line.startsWith("model name")) {
        String[] parts = line.split(":", 2);
        if (parts.length == 2 && !info.containsKey("cpu_model")) {
          info.put("cpu_model", parts[1].trim());
          break;
        }
      }
    }
  }

  /**
   * Collects CPU information on macOS systems using sysctl.
   *
   * @param info Map to populate with CPU information
   */
  private static void collectMacCpuInfo(Map<String, Object> info) {
    try {
      String sysctlOutput = executeCommand("sysctl -a");
      if (sysctlOutput != null && !sysctlOutput.isEmpty()) {
        parseSysctlOutput(sysctlOutput, info);
      }

      // Try to get CPU brand string
      String cpuBrand = executeCommand("sysctl -n machdep.cpu.brand_string");
      if (cpuBrand != null && !cpuBrand.isEmpty()) {
        info.put("cpu_model", cpuBrand.trim());
      }

    } catch (Exception e) {
      LOG.warning("Failed to collect macOS CPU info: " + e.getMessage());
    }
  }

  /**
   * Parses sysctl output to extract CPU information on macOS.
   *
   * @param output sysctl command output
   * @param info Map to populate with CPU information
   */
  private static void parseSysctlOutput(String output, Map<String, Object> info) {
    for (String line : output.split("\n")) {
      if (line.contains("machdep.cpu")) {
        String[] parts = line.split(":", 2);
        if (parts.length == 2) {
          String key = parts[0].trim();
          String value = parts[1].trim();

          if (key.equals("machdep.cpu.core_count")) {
            try {
              info.put("cpu_physical_cores", Integer.parseInt(value));
            } catch (NumberFormatException e) {
              // Ignore
            }
          } else if (key.equals("machdep.cpu.thread_count")) {
            try {
              info.put("cpu_logical_cores", Integer.parseInt(value));
            } catch (NumberFormatException e) {
              // Ignore
            }
          }
        }
      } else if (line.contains("hw.cpufrequency")) {
        String[] parts = line.split(":", 2);
        if (parts.length == 2) {
          try {
            long freqHz = Long.parseLong(parts[1].trim());
            info.put("cpu_freq_mhz", freqHz / 1_000_000);
          } catch (NumberFormatException e) {
            // Ignore
          }
        }
      }
    }
  }

  /**
   * Collects CPU information on Windows systems using WMIC.
   *
   * @param info Map to populate with CPU information
   */
  private static void collectWindowsCpuInfo(Map<String, Object> info) {
    try {
      String wmicOutput =
          executeCommand(
              "wmic cpu get Name,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed /format:list");
      if (wmicOutput != null && !wmicOutput.isEmpty()) {
        parseWmicOutput(wmicOutput, info);
      }
    } catch (Exception e) {
      LOG.warning("Failed to collect Windows CPU info: " + e.getMessage());
    }
  }

  /**
   * Parses WMIC output to extract CPU information on Windows.
   *
   * @param output WMIC command output
   * @param info Map to populate with CPU information
   */
  private static void parseWmicOutput(String output, Map<String, Object> info) {
    for (String line : output.split("\n")) {
      if (line.contains("=")) {
        String[] parts = line.split("=", 2);
        if (parts.length == 2) {
          String key = parts[0].trim();
          String value = parts[1].trim();

          switch (key) {
            case "Name":
              info.put("cpu_model", value);
              break;
            case "NumberOfCores":
              try {
                info.put("cpu_physical_cores", Integer.parseInt(value));
              } catch (NumberFormatException e) {
                // Ignore
              }
              break;
            case "NumberOfLogicalProcessors":
              try {
                info.put("cpu_logical_cores", Integer.parseInt(value));
              } catch (NumberFormatException e) {
                // Ignore
              }
              break;
            case "MaxClockSpeed":
              try {
                info.put("cpu_max_mhz", Double.parseDouble(value));
              } catch (NumberFormatException e) {
                // Ignore
              }
              break;
            default:
              // Ignore
          }
        }
      }
    }
  }

  /**
   * Detects if running in a container or VM environment.
   *
   * @param info Map to populate with environment information
   */
  private static void detectContainerEnvironment(Map<String, Object> info) {
    // Check for Docker
    if (System.getenv("DOCKER_CONTAINER") != null
        || java.nio.file.Files.exists(java.nio.file.Paths.get("/.dockerenv"))) {
      info.put("container_type", "docker");
    }

    // Check for GitHub Actions
    if (System.getenv("GITHUB_ACTIONS") != null) {
      info.put("ci_environment", "github_actions");
      String runner = System.getenv("RUNNER_NAME");
      if (runner != null) {
        info.put("ci_runner", runner);
      }
    }

    // Check for other CI environments
    if (System.getenv("CI") != null) {
      info.put("is_ci", true);
    }
  }

  /**
   * Executes a shell command and returns its output.
   *
   * @param command Command to execute
   * @return Command output or null if failed
   */
  private static String executeCommand(String command) {
    Process process = null;
    try {
      ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
      process = pb.start();
      StringBuilder output = new StringBuilder();
      StringBuilder errorOutput = new StringBuilder();
      // Read stdout and stderr fully to avoid deadlocks
      try (BufferedReader stdoutReader =
               new BufferedReader(
                   new InputStreamReader(
                       process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
           BufferedReader stderrReader =
               new BufferedReader(
                   new InputStreamReader(
                       process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = stdoutReader.readLine()) != null) {
          output.append(line).append("\n");
        }
        // Consume stderr fully (discard or log)
        while ((line = stderrReader.readLine()) != null) {
          // Optionally collect error output
          errorOutput.append(line).append("\n");
        }
      }
      int exitCode = process.waitFor();
      if (exitCode == 0) {
        return output.toString();
      }
      // Optionally, could log errorOutput if needed
      return null;
    } catch (Exception e) {
      return null;
    } finally {
      if (process != null) {
        try {
          process.getInputStream().close();
        } catch (Exception ignored) {}
        try {
          process.getErrorStream().close();
        } catch (Exception ignored) {}
        try {
          process.getOutputStream().close();
        } catch (Exception ignored) {}
      }
    }
  }

  /**
   * Generates a unique identifier for the current hardware configuration.
   *
   * <p>This identifier is used to match baselines from the same architecture.
   *
   * @param systemInfo System information map
   * @return Architecture identifier string
   */
  public static String generateArchitectureId(Map<String, Object> systemInfo) {
    StringBuilder id = new StringBuilder();

    // Include key identifying information
    id.append(systemInfo.getOrDefault("os_arch", "unknown"));
    id.append("_");

    // Sanitize and truncate CPU model to avoid overly long IDs
    String cpuModel = systemInfo.getOrDefault("cpu_model", "unknown").toString();
    String sanitizedModel =
        cpuModel.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");

    // Truncate to 50 characters max to keep ID manageable
    if (sanitizedModel.length() > 50) {
      sanitizedModel = sanitizedModel.substring(0, 50);
    }

    id.append(sanitizedModel);
    id.append("_");
    id.append(systemInfo.getOrDefault("cpu_logical_cores", "0"));

    return id.toString();
  }
}
