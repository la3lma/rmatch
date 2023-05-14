package no.rmz.rmatch.performancetests;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker.TestRunResult;
import no.rmz.rmatch.utils.StringBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.*;
import static no.rmz.rmatch.performancetests.utils.MatcherBenchmarker.testACorpusNG;

/**
 * A test scenario that will match a bunch (by default 10K) of regular expressions against the
 * text of Emily Brontes Wuthering Heights.
 */
public final class BenchmarkLargeCorpus {

    private static String byteArrayToHexString(byte[] b) {
        String result = "";
        for (int i=0; i < b.length; i++) {
            result +=
                    Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }

    /**
     * The main method.
     *
     * @param argv Command line arguments. If present, arg 1 is interpreted
     *             as a maximum limit on the number of regexps to use.
     *             The second parameter is the path to the list of the regular expressions,
     *             the remaining parameters are the names of the files to be searched through using
     *             the regexps.
     * @throws RegexpParserException when things go bad.
     */
    public static void main(final String[] argv) throws RegexpParserException {

        String cwd = Paths.get(".").toAbsolutePath().normalize().toString();
        System.out.println("Current working directory is " + cwd);
        // Parse command line

        // TODO: Get these from the command line:
        String logfile = "logs/large-corpus-log.csv";
        String testSeriesId = valueOf(UUID.randomUUID());

        if (argv.length < 3) {
            System.err.println("Not enough parameters. Require at least 3: no of regexps, file where regexps are stored, one or more files to search through");
            System.exit(1);
        }

        String nameOfRegexpFile = argv[1];
        if (!new File(nameOfRegexpFile).exists()) {
            System.err.println("Regexp file does not exist:'" + nameOfRegexpFile + "'");
            System.exit(1);
        }

        List<String> allRegexps = null;
        try {
            allRegexps = Files.readAllLines(Paths.get(nameOfRegexpFile));
        } catch (IOException e) {
            System.err.println("Couldn't read regexps from file:'" + nameOfRegexpFile + "'");
            System.exit(1);
        }

        // Clean up the set of regexps a little.
        allRegexps = allRegexps.stream().filter(c -> c.trim().length() != 0).distinct().collect(Collectors.toUnmodifiableList());

        String algorithm = "SHA-1";

        // Then scramble their ordering based on their hash
        final Map<String, String> regexMap = new TreeMap<>();
        for (String r : allRegexps) {
            MessageDigest md = null;
            try {
                md = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                System.err.println("Could not find digester algorithm '" + algorithm + "'");
                System.exit(1);
            }
            regexMap.put(byteArrayToHexString(md.digest(r.getBytes())), r);
        }

        allRegexps = regexMap.entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toUnmodifiableList());

        int noOfRegexps = Integer.parseInt(argv[0]);
        if (noOfRegexps == -1) {
            noOfRegexps = allRegexps.size();
            System.out.println("Using all regexps: " + noOfRegexps);
        } else if (noOfRegexps < -1) {
            System.out.println("Number of regexps must be nonegative (or -1 to indicate all)");
            System.exit(1);
        } else if (noOfRegexps > allRegexps.size()) {
            System.out.println("max number of regexps is: " + noOfRegexps + ", using that");
        }

        StringBuilder corpus = new StringBuilder();
        for (int i = 2; i < argv.length; i++) {
            String filePath = argv[i];
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("Corpus file does not exist:'" + filePath + "'");
                System.exit(1);
            }
            try (Stream<String> stream = Files
                    .lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
                stream.forEach(s -> corpus.append(s).append("\n"));
            } catch (IOException e) {
                System.err.println("Something went wrong while reading file '" + filePath + "'");
                System.exit(1);
            }
        }

        // Report what we intend to do
        System.out.println("About to match " + noOfRegexps + " regexps from " + "'" + nameOfRegexpFile + "'" + " then match them against a bunch of files");
        System.out.println("that contains in total " + corpus.length() + " characters");

        // TODO: Make sure that the results from the matchers are identical (right now they are not!)
        //           -- Do that using a matcher that builds up a structure that can be used to compare results
        //              after the runs.
        //       Make sure that they are also reasonable
        //       also permit running the tests multiple time
        //       store the results in some appropriate format (csv, or sqlite comes to mind)

        // Then run the test
        final Matcher m = MatcherFactory.newMatcher();
        Buffer buf = new StringBuffer(corpus.toString());
        List<String> regexps;
        regexps = allRegexps.subList(0, noOfRegexps - 1);

        System.out.println("========");
        System.out.println("Run the native matcher");
        // Run the regex matcher
        TestRunResult rmatchResult = testACorpusNG("rmatch", m, regexps, buf);

        System.gc();
        // Run the java regex  matcher
        System.out.println("========");
        System.out.println("Run the java matcher");
        JavaRegexpMatcher jm = new JavaRegexpMatcher();
        TestRunResult javaResult = testACorpusNG("java", jm, regexps, buf);

        // Describe the test runs individually
        describeTestResult(rmatchResult);
        describeTestResult(javaResult);

        int numberOfCorrespondingMatchesDetected = 0;
        int numberOfMismatchesDetected = 0;

        // Traverse the java matches and try to find matching rmatch matches
        ArrayList<MatcherBenchmarker.LoggedMatch> matches = new ArrayList<>();
        for (var k : javaResult.loggedMatches()) {
            if (rmatchResult.loggedMatches().contains(k)) {
                matches.add(k);
                numberOfCorrespondingMatchesDetected += 1;
            } else {
                numberOfMismatchesDetected += 1;
            }
        }

        for (var k: matches) {
            javaResult.loggedMatches().remove(k);
            rmatchResult.loggedMatches().remove(k);
        }

        matches.clear();

        // Then do the same for the rmatch matches
        for (var n : rmatchResult.loggedMatches()) {
            if (javaResult.loggedMatches().contains(n)) {
                matches.add(n);
                numberOfCorrespondingMatchesDetected += 1;
            } else {
                numberOfMismatchesDetected += 1;
            }
        }

        for (var k: matches) {
            javaResult.loggedMatches().remove(k);
            rmatchResult.loggedMatches().remove(k);
        }

        if (numberOfMismatchesDetected != (javaResult.loggedMatches().size() + rmatchResult.loggedMatches().size())) {
            System.err.println("Double mismatch detected");
        }

        // Todo: Look at only the first mismatch, in the java set.  See where the match with the least
        //       difference to the one not matching exists in the other set (the original set). Also seee which
        //       matches _do_ exist for the interval being covered by the mismatched element and report that.
        //       The hypotheses are that there is either an alignment problem, a dominance problem, or simply that
        //       the expression itself wasn't encoded in the (rmatch) matcher.  We need to eek out these issues
        //       and figure out what fails.



        System.out.println("\nNumber of mismatches = " + numberOfMismatchesDetected);
        System.out.println("Number of matches    = " + numberOfCorrespondingMatchesDetected);
        System.out.println("Sum of matches and mismatches = " + (numberOfMismatchesDetected + numberOfCorrespondingMatchesDetected));


        MatcherBenchmarker.TestPairSummary result =
            new MatcherBenchmarker.TestPairSummary(System.currentTimeMillis(), testSeriesId,
                rmatchResult.matcherTypeName(), rmatchResult.usedMemoryInMb(), rmatchResult.durationInMillis(),
                javaResult.matcherTypeName(), javaResult.usedMemoryInMb(), javaResult.durationInMillis(),
                numberOfCorrespondingMatchesDetected,
                numberOfMismatchesDetected,
                    noOfRegexps,
                    corpus.length()
                );

        MatcherBenchmarker.writeSummaryToFile(logfile, result);

        System.exit(0);
    }

    private static void describeTestResult(TestRunResult rmatchResult) {
        System.out.println("-------");
        System.out.println("Name: " + rmatchResult.matcherTypeName());
        System.out.println("Time: " + rmatchResult.durationInMillis() + " ms");
        System.out.println("No of logged matches: " + rmatchResult.loggedMatches().size());
        System.out.println("Megabytes used: " + rmatchResult.usedMemoryInMb());
    }

    /**
     * No public constructor in an utility class.
     */
    private BenchmarkLargeCorpus() {
    }
}
