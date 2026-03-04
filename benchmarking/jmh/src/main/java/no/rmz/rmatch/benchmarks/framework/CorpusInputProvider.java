package no.rmz.rmatch.benchmarks.framework;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Provides corpus-based input for JMH benchmarks using Wuthering Heights and related files.
 *
 * <p>This class loads text corpora and regex patterns from the rmatch-tester/corpus directory to
 * provide realistic test data for performance benchmarking.
 */
public final class CorpusInputProvider {

  private static final Logger LOG = Logger.getLogger(CorpusInputProvider.class.getName());

  public enum CorpusSize {
    VERY_FEW(
        "very-few-words-in-wuthering-heigths.txt",
        "corpus/very-few-words-in-wuthering-heigths.txt"),
    SOME("some-words-in-wuthering-heigths.txt", "corpus/some-words-in-wuthering-heigths.txt"),
    MANY("words-in-wuthering-heigths.txt", "corpus/words-in-wuthering-heigths.txt"),
    ALL("real-words-in-wuthering-heights.txt", "corpus/real-words-in-wuthering-heights.txt");

    private final String filename;
    private final String relativePath;

    CorpusSize(String filename, String relativePath) {
      this.filename = filename;
      this.relativePath = relativePath;
    }

    public String getFilename() {
      return filename;
    }

    public String getRelativePath() {
      return relativePath;
    }
  }

  public enum TextCorpus {
    WUTHERING_HEIGHTS("wuthr10.txt", "corpus/wuthr10.txt"),
    CRIME_AND_PUNISHMENT(
        "crime-and-punishment-by-dostoyevsky.txt",
        "corpus/crime-and-punishment-by-dostoyevsky.txt"),
    SHERLOCK_HOLMES("sherlock-holmes.txt", "corpus/sherlock-holmes.txt"),
    UNIQUE_WORDS("unique-words.txt", "corpus/unique-words.txt");

    private final String filename;
    private final String relativePath;

    TextCorpus(String filename, String relativePath) {
      this.filename = filename;
      this.relativePath = relativePath;
    }

    public String getFilename() {
      return filename;
    }

    public String getRelativePath() {
      return relativePath;
    }
  }

  /**
   * Load text content from a corpus file.
   *
   * @param corpus The text corpus to load
   * @return The full text content as a string
   * @throws IOException if the file cannot be read
   */
  public static String loadTextCorpus(TextCorpus corpus) throws IOException {
    Path corpusPath = findCorpusFile(corpus.getRelativePath());
    String content = Files.readString(corpusPath, StandardCharsets.UTF_8);
    LOG.info(
        "Loaded text corpus " + corpus.getFilename() + " with " + content.length() + " characters");
    return content;
  }

  /**
   * Load regex patterns from a corpus pattern file.
   *
   * @param corpusSize The size/type of pattern set to load
   * @return List of regex patterns as strings
   * @throws IOException if the file cannot be read
   */
  public static List<String> loadRegexPatterns(CorpusSize corpusSize) throws IOException {
    Path patternPath = findCorpusFile(corpusSize.getRelativePath());
    List<String> patterns = Files.readAllLines(patternPath, StandardCharsets.UTF_8);

    // Filter out empty lines and convert to lowercase for pattern matching
    List<String> validPatterns = new ArrayList<>();
    for (String pattern : patterns) {
      String trimmed = pattern.trim();
      if (!trimmed.isEmpty()) {
        // Convert to lowercase literal pattern (escape regex special chars if needed)
        validPatterns.add(trimmed.toLowerCase());
      }
    }

    LOG.info("Loaded " + validPatterns.size() + " regex patterns from " + corpusSize.getFilename());
    return validPatterns;
  }

  /**
   * Find the corpus file, trying multiple possible locations.
   *
   * @param relativePath The relative path to the corpus file
   * @return Path to the corpus file
   * @throws IOException if the file cannot be found
   */
  private static Path findCorpusFile(String relativePath) throws IOException {
    // Try multiple possible locations
    String[] possibleRoots = {".", "..", "../..", "../../..", System.getProperty("user.dir")};

    for (String root : possibleRoots) {
      Path candidate = Paths.get(root, "rmatch-tester", relativePath.replace("corpus/", "corpus/"));
      if (Files.exists(candidate)) {
        LOG.fine("Found corpus file at: " + candidate.toAbsolutePath());
        return candidate;
      }
    }

    // Also try direct relative path from current directory
    Path directPath = Paths.get("rmatch-tester", relativePath);
    if (Files.exists(directPath)) {
      LOG.fine("Found corpus file at: " + directPath.toAbsolutePath());
      return directPath;
    }

    throw new IOException(
        "Cannot find corpus file: "
            + relativePath
            + " (tried various locations relative to current dir: "
            + System.getProperty("user.dir")
            + ")");
  }

  /**
   * Create test patterns from a list of words/regexes.
   *
   * @param words List of words to convert to TestPattern objects
   * @param category Pattern category
   * @param maxPatterns Maximum number of patterns to create
   * @return List of TestPattern objects
   */
  public static List<TestPattern> createTestPatternsFromWords(
      List<String> words, PatternCategory category, int maxPatterns) {
    List<TestPattern> patterns = new ArrayList<>();
    int count = Math.min(words.size(), maxPatterns);

    for (int i = 0; i < count; i++) {
      String word = words.get(i).trim().toLowerCase();
      if (!word.isEmpty()) {
        PatternMetadata metadata =
            new PatternMetadata(
                "corpus_word_" + i,
                "Word from corpus: " + word,
                category,
                2, // complexity
                false // not pathological
                );
        patterns.add(new TestPattern(word, metadata));
      }
    }

    LOG.info("Created " + patterns.size() + " test patterns from corpus words");
    return patterns;
  }
}
