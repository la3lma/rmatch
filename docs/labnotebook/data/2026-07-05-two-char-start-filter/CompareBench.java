import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Standalone comparison driver for java.util.regex and RE2J, using the same protocol and CSV
 * format as CascadeBench: same regexp selection, same corpus construction, timing of the scan
 * phase only (compilation excluded), warmup iterations discarded.
 *
 * <p>Both engines are run the way a user without a multi-pattern matcher would run them: one
 * compiled pattern per regexp, one full scan of the corpus per pattern, counting all
 * non-overlapping occurrences per pattern. Note the caveat: match SEMANTICS differ from rmatch
 * (no cross-pattern domination), so match counts are not comparable to rmatch's — only the
 * throughput is.
 *
 * <p>Usage: java CompareBench &lt;variant&gt; &lt;regexpFile&gt; &lt;nRegexps&gt; &lt;corpusFile&gt;
 * &lt;corpusMultiplier&gt; &lt;warmup&gt; &lt;measured&gt; &lt;engine: javanative|re2j&gt;
 */
public final class CompareBench {

  public static void main(final String[] args) throws Exception {
    final String variant = args[0];
    final String regexpFile = args[1];
    final int nRegexps = Integer.parseInt(args[2]);
    final String corpusFile = args[3];
    final int mult = Integer.parseInt(args[4]);
    final int warmup = Integer.parseInt(args[5]);
    final int measured = Integer.parseInt(args[6]);
    final String engine = args[7];

    final List<String> regexps = readRegexps(regexpFile, nRegexps);
    final String corpus = Files.readString(Paths.get(corpusFile), StandardCharsets.UTF_8).repeat(mult);

    System.err.printf(
        "variant=%s engine=%s nRegexps=%d corpusChars=%d%n",
        variant, engine, regexps.size(), corpus.length());

    for (int i = 0; i < warmup + measured; i++) {
      final boolean isWarmup = i < warmup;
      final long[] result =
          switch (engine) {
            case "javanative" -> runJavaNative(regexps, corpus);
            case "re2j" -> runRe2j(regexps, corpus);
            default -> throw new IllegalArgumentException("unknown engine " + engine);
          };
      System.out.printf(
          "RESULT,%s,%s,%d,%d,%d,%s,%d,%d%n",
          variant,
          engine,
          regexps.size(),
          corpus.length(),
          i,
          isWarmup ? "warmup" : "measured",
          result[0] / 1_000_000,
          result[1]);
      System.out.flush();
    }
  }

  /** Returns {scanNanos, totalMatches}. Compilation happens outside the timed section. */
  private static long[] runJavaNative(final List<String> regexps, final String corpus) {
    final List<java.util.regex.Pattern> compiled = new ArrayList<>(regexps.size());
    for (final String r : regexps) {
      compiled.add(java.util.regex.Pattern.compile(r));
    }
    long matches = 0;
    final long t0 = System.nanoTime();
    for (final java.util.regex.Pattern p : compiled) {
      final java.util.regex.Matcher m = p.matcher(corpus);
      while (m.find()) {
        matches++;
      }
    }
    final long t1 = System.nanoTime();
    return new long[] {t1 - t0, matches};
  }

  private static long[] runRe2j(final List<String> regexps, final String corpus) {
    final List<com.google.re2j.Pattern> compiled = new ArrayList<>(regexps.size());
    for (final String r : regexps) {
      compiled.add(com.google.re2j.Pattern.compile(r));
    }
    long matches = 0;
    final long t0 = System.nanoTime();
    for (final com.google.re2j.Pattern p : compiled) {
      final com.google.re2j.Matcher m = p.matcher(corpus);
      while (m.find()) {
        matches++;
      }
    }
    final long t1 = System.nanoTime();
    return new long[] {t1 - t0, matches};
  }

  private static List<String> readRegexps(final String file, final int n) throws Exception {
    final LinkedHashSet<String> out = new LinkedHashSet<>();
    for (final String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
      final String t = line.trim();
      if (!t.isEmpty()) {
        out.add(t);
      }
      if (out.size() == n) {
        break;
      }
    }
    return new ArrayList<>(out);
  }

  private CompareBench() {}
}
