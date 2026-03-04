import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaRegexBenchmark {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: JavaRegexBenchmark <patterns-file> <corpus-file>");
            System.exit(1);
        }
        Path patternsPath = Path.of(args[0]);
        Path corpusPath = Path.of(args[1]);

        List<Pattern> patterns = loadPatterns(patternsPath);
        List<String> corpus = Files.readAllLines(corpusPath);

        long start = System.nanoTime();
        long matches = countMatches(patterns, corpus);
        long elapsed = System.nanoTime() - start;

        System.out.println("MATCHES=" + matches);
        System.out.println("ELAPSED_NS=" + elapsed);
    }

    private static List<Pattern> loadPatterns(Path path) throws IOException {
        List<Pattern> patterns = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(Pattern.compile(trimmed));
            }
        }
        return patterns;
    }

    private static long countMatches(List<Pattern> patterns, List<String> corpus) {
        long matches = 0;
        for (String text : corpus) {
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    matches++;
                }
            }
        }
        return matches;
    }
}
