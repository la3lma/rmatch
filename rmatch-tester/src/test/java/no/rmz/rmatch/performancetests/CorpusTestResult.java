package no.rmz.rmatch.performancetests;

/**
 * The result of reading a corpus.  This record will
 * be used when logging the results of the test run.
 */
public final class CorpusTestResult {
    /**
     * The file that was read.
     */
    private final String filename;
    /**
     * The duration of the test.
     */
    private final long duration;
    /**
     * The number of matches that was found.
     */
    private final long noOfMatches;
    /**
     * The number of words that were looked for.
     */
    private final long noOfWordsToLookFor;
    /**
     * The number of megabytes that was used during the match (max).
     */
    private final long maxNoOfMbsUsed;

    /**
     * Create a new test result for processing a corpus.
     * @param filename The filename where the corpus is located.
     * @param duration the duration of the test.
     * @param noOfMatches The number of matches found.
     * @param noOfWordsToLookFor The number of words to look for.
     * @param maxNoOfMbsUsed  The number of megabytes use (max) during the test
     *                        run.
     */
    public CorpusTestResult(
            final String filename,
            final long duration,
            final long noOfMatches,
            final long noOfWordsToLookFor,
            final long maxNoOfMbsUsed) {
        this.filename = filename;
        this.duration = duration;
        this.noOfMatches = noOfMatches;
        this.noOfWordsToLookFor = noOfWordsToLookFor;
        this.maxNoOfMbsUsed = maxNoOfMbsUsed;
    }

    /**
     * Get the filename.
     * @return  the filename.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Get the duration in milliseconds.
     * @return the duration.
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Get the number of matches.
     * @return  # of matches.
     */
    public long getNoOfMatches() {
        return noOfMatches;
    }

    /**
     * Get the number of words to look for.
     * @return # of words.
     */
    public long getNoOfWordsToLookFor() {
        return noOfWordsToLookFor;
    }

    /**
     * Get the number of megabytes that was used.
     * @return # of megabytes.
     */
    public long getMaxNoOfMbsUsed() {
        return maxNoOfMbsUsed;
    }

    @Override
    public String toString() {
        return "CorpusTestResult{" + "filename=" + filename + ", duration=" + duration + ", noOfMatches=" + noOfMatches + ", noOfWordsToLookFor=" + noOfWordsToLookFor + ", maxNoOfMbsUsed=" + maxNoOfMbsUsed + '}';
    }
}
