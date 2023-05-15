package no.rmz.rmatch.performancetests;

import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.google.common.base.Preconditions.checkNotNull;
import static no.rmz.rmatch.performancetests.utils.MatcherBenchmarker.matchComparator;

final class MatchPairAnalysis {

    private final MatcherBenchmarker.TestRunResult originalJavaResult;
    private final MatcherBenchmarker.TestRunResult originalRmatchResult;
    private final SortedSet<MatcherBenchmarker.LoggedMatch> javaMatches;
    private final SortedSet<MatcherBenchmarker.LoggedMatch> rmatchMatches;
    private final MatcherBenchmarker.TestPairSummary result;
    private int numberOfMismatchesDetected;
    private int numberOfCorrespondingMatchesDetected;
    private boolean doubleMismatchDetected;

    public MatchPairAnalysis(
            final String testSeriesId,
            final String metadata,
            final int noOfRegexps,
            final int corpusLength,
            final MatcherBenchmarker.TestRunResult javaResult,
            final MatcherBenchmarker.TestRunResult rmatchResult) {

        // Vetting parameters
        checkNotNull(testSeriesId);
        checkNotNull(metadata);
        checkNotNull(javaResult);
        checkNotNull(rmatchResult);

        if (!javaResult.matcherTypeName().equals("java")) {
            System.err.println("Java result parameter does not have matcher type java, was '"
                    + javaResult.matcherTypeName() + "'");
        }
        if (!rmatchResult.matcherTypeName().equals("rmatch")) {
            System.err.println("Java result parameter does not have matcher type java, was '"
                    + rmatchResult.matcherTypeName() + "'");
        }

        this.originalJavaResult = javaResult;
        this.originalRmatchResult = rmatchResult;

        this.javaMatches = new TreeSet<>(matchComparator);
        this.javaMatches.addAll(javaResult.loggedMatches());
        this.rmatchMatches = new TreeSet<>(matchComparator);
        this.rmatchMatches.addAll(rmatchResult.loggedMatches());

        findMismatches(javaMatches, rmatchMatches);

        // Analyze first java mismatch (if any)
        if (javaMatches.size() > 0) {
            MatcherBenchmarker.LoggedMatch jm = javaMatches.first();
            // Find matches that span the same interval in the regexp match set
            System.out.println("Analyzing mismatching java match: " + jm);
            for (var rm: this.originalRmatchResult.loggedMatches()) {
                if (rm.start() <= jm.start() && rm.end() >= jm.end()) {
                    System.out.println("  rm match dominating: " + rm);
                }
                if (jm.start() <= rm.start() && rm.start() <= jm.end()) {
                    System.out.println("  rm start inside jm: " + rm);
                }
                if (jm.start() <= rm.end() && rm.end() <= jm.end()) {
                    System.out.println("  rm end inside jm: " + rm);
                }

                if (jm.start() <= rm.start() && rm.end() >= jm.end()) {
                    System.out.println("  jm match dominating: " + jm);
                }
                if (rm.start() <= jm.start() && jm.start() <= rm.end()) {
                    System.out.println("  jm start inside rm: " + rm);
                }
                if (rm.start() <= jm.end() && jm.end() <= rm.end()) {
                    System.out.println("  jm end inside rm: " + jm);
                }
            }
        }

        this.result =
                new MatcherBenchmarker.TestPairSummary(System.currentTimeMillis(), testSeriesId, metadata,
                        rmatchResult.matcherTypeName(), rmatchResult.usedMemoryInMb(), rmatchResult.durationInMillis(),
                        javaResult.matcherTypeName(), javaResult.usedMemoryInMb(), javaResult.durationInMillis(),
                        numberOfCorrespondingMatchesDetected,
                        numberOfMismatchesDetected,
                        noOfRegexps,
                        corpusLength
                );
    }

    private void unidirectionalFindMismatches(
            final SortedSet<MatcherBenchmarker.LoggedMatch> a,
            final SortedSet<MatcherBenchmarker.LoggedMatch> b){
        ArrayList<MatcherBenchmarker.LoggedMatch> matches = new ArrayList<>();
        for (var k : a) {
            if (b.contains(k)) {
                matches.add(k);
                this.numberOfCorrespondingMatchesDetected += 1;
            } else {
                this.numberOfMismatchesDetected += 1;
            }
        }

        for (var k : matches) {
            a.remove(k);
            b.remove(k);
        }
    }

    private void findMismatches(
            final SortedSet<MatcherBenchmarker.LoggedMatch> a,
            final SortedSet<MatcherBenchmarker.LoggedMatch> b) {
        unidirectionalFindMismatches(a, b);
        unidirectionalFindMismatches(b, a);

        if (this.numberOfMismatchesDetected != (a.size() + b.size())) {
            System.err.println("Double mismatch detected");
            this.doubleMismatchDetected = true;
        }
    }

    public void printSummary(PrintStream out) {
        out.println("\nNumber of mismatches = " + numberOfMismatchesDetected);
        out.println("Number of matches    = " + numberOfCorrespondingMatchesDetected);
        out.println("Sum of matches and mismatches = " + (numberOfMismatchesDetected + numberOfCorrespondingMatchesDetected));
    }

    public MatcherBenchmarker.TestPairSummary getResult() {
        return result;
    }
}
