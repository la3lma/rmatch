import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Naive java.util.regex benchmark implementation.
 *
 * This intentionally uses a straightforward baseline approach:
 * - Compile all patterns one by one
 * - Run each compiled pattern across the whole corpus
 * - Count all non-overlapping matches
 */
public class JavaNativeNaiveBenchmark {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java JavaNativeNaiveBenchmark <patterns-file> <corpus-file>");
            System.exit(1);
        }

        String patternsFile = args[0];
        String corpusFile = args[1];

        try {
            List<String> patterns = Files.readAllLines(Paths.get(patternsFile));
            String corpus = Files.readString(Paths.get(corpusFile));

            long compileStartNs = System.nanoTime();

            List<Pattern> compiledPatterns = new ArrayList<>();
            int patternsCompiled = 0;
            int patternsFailed = 0;

            for (String rawPattern : patterns) {
                String pattern = rawPattern.trim();
                if (pattern.isEmpty()) {
                    continue;
                }
                try {
                    compiledPatterns.add(Pattern.compile(pattern));
                    patternsCompiled++;
                } catch (PatternSyntaxException e) {
                    patternsFailed++;
                }
            }

            long compileEndNs = System.nanoTime();
            long compilationNs = compileEndNs - compileStartNs;

            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            runtime.runFinalization();
            runtime.gc();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            long scanStartNs = System.nanoTime();
            int totalMatches = 0;

            for (Pattern pattern : compiledPatterns) {
                Matcher matcher = pattern.matcher(corpus);
                while (matcher.find()) {
                    totalMatches++;
                }
            }

            long scanEndNs = System.nanoTime();
            long elapsedNs = scanEndNs - scanStartNs;

            runtime.gc();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryBytes = Math.max(memoryAfter - memoryBefore, memoryAfter);

            System.out.println("COMPILATION_NS=" + compilationNs);
            System.out.println("ELAPSED_NS=" + elapsedNs);
            System.out.println("MATCHES=" + totalMatches);
            System.out.println("MEMORY_BYTES=" + memoryBytes);
            System.out.println("PATTERNS_COMPILED=" + patternsCompiled);
            System.out.println("PATTERNS_FAILED=" + patternsFailed);
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
