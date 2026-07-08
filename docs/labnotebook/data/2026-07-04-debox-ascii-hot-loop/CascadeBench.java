import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.Buffer;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.utils.RegexStringBuffer;

/**
 * Standalone cascade benchmark driver for A/B experiments on rmatch.
 *
 * <p>Deliberately lives OUTSIDE the repo so the identical driver bytecode is used against both
 * baseline and candidate jars.
 *
 * <p>Usage: java CascadeBench &lt;variantLabel&gt; &lt;regexpFile&gt; &lt;nRegexps&gt;
 * &lt;corpusFile&gt; &lt;corpusMultiplier&gt; &lt;warmupIters&gt; &lt;measuredIters&gt;
 * &lt;impl:single|factory&gt;
 *
 * <p>Emits CSV rows on stdout prefixed with "RESULT," :
 * variant,impl,nRegexps,corpusChars,iter,phase,match_ms,totalMatches
 */
public final class CascadeBench {

  public static void main(final String[] args) throws Exception {
    final String variant = args[0];
    final String regexpFile = args[1];
    final int nRegexps = Integer.parseInt(args[2]);
    final String corpusFile = args[3];
    final int corpusMultiplier = Integer.parseInt(args[4]);
    final int warmup = Integer.parseInt(args[5]);
    final int measured = Integer.parseInt(args[6]);
    final String impl = args[7];

    final List<String> regexps = readRegexps(regexpFile, nRegexps);
    final String corpus = readCorpus(corpusFile, corpusMultiplier);

    System.err.printf(
        "variant=%s impl=%s nRegexps=%d corpusChars=%d warmup=%d measured=%d%n",
        variant, impl, regexps.size(), corpus.length(), warmup, measured);

    Long expectedMatches = null;
    for (int i = 0; i < warmup + measured; i++) {
      final boolean isWarmup = i < warmup;
      final Matcher m = newMatcher(impl);
      final AtomicLong counter = new AtomicLong();
      for (final String r : regexps) {
        m.add(r, (b, start, end) -> counter.incrementAndGet());
      }
      final Buffer buf = new RegexStringBuffer(corpus);
      final long t0 = System.nanoTime();
      m.match(buf);
      final long t1 = System.nanoTime();
      m.shutdown();
      final long matches = counter.get();
      if (expectedMatches == null) {
        expectedMatches = matches;
      } else if (matches != expectedMatches) {
        System.out.printf(
            "RESULT,%s,%s,%d,%d,%d,ERROR_MATCH_MISMATCH,%d,%d%n",
            variant, impl, regexps.size(), corpus.length(), i, (t1 - t0) / 1_000_000, matches);
        System.exit(2);
      }
      System.out.printf(
          "RESULT,%s,%s,%d,%d,%d,%s,%d,%d%n",
          variant,
          impl,
          regexps.size(),
          corpus.length(),
          i,
          isWarmup ? "warmup" : "measured",
          (t1 - t0) / 1_000_000,
          matches);
      System.out.flush();
    }
    System.exit(0);
  }

  private static Matcher newMatcher(final String impl) {
    if ("factory".equals(impl)) {
      return MatcherFactory.newMatcher();
    }
    return new MatcherImpl(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
  }

  private static List<String> readRegexps(final String file, final int n) throws Exception {
    // Deterministic: first n distinct non-blank trimmed lines, in file order.
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

  private static String readCorpus(final String file, final int multiplier) throws Exception {
    final String once = Files.readString(Paths.get(file), StandardCharsets.UTF_8);
    return once.repeat(multiplier);
  }

  private CascadeBench() {}
}
