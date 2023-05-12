package no.rmz.rmatch.performancetests;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import no.rmz.rmatch.utils.StringBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * A test scenario that will match a bunch (by default 10K) of regular expressions against the
 * text of Emily Brontes Wuthering Heights.
 */
public final class BenchmarkLargeCorpus {

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

        if (argv.length < 3) {
            System.err.println("Not enough parameters. Require at least 3: no of regexps, file where regexps are stored, one or more files to search through");
            System.exit(1);
        }

        int noOfRegexps = Integer.parseInt(argv[0]);
        if (noOfRegexps <= 0) {
            System.err.println("Number of regexps to use must be positive");
            System.exit(1);
        }

        String nameOfRegexpFile = argv[1];
        if (!new File(nameOfRegexpFile).exists()) {
            System.err.println("Regexp file does not exist:'" + nameOfRegexpFile+ "'");
            System.exit(1);
        }

        List<String> allRegexps = null;
        try {
            allRegexps = Files.readAllLines(Paths.get(nameOfRegexpFile));
        } catch (IOException e) {
            System.err.println("Couldn't read regexps from file:'" + nameOfRegexpFile+ "'");
            System.exit(1);
        }

        StringBuilder corpus = new StringBuilder();
        for (int i = 2; i < argv.length;  i++) {
            String filePath = argv[i];
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("Corpus file does not exist:'" + filePath+ "'");
                System.exit(1);
            }
            try (Stream<String> stream = Files
                    .lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
                stream.forEach(s -> corpus.append(s).append("\n"));
            } catch (IOException e) {
                System.err.println("Something went wrong while reading file '" + filePath+ "'");
                System.exit(1);
            }
        }

        // Report what we intend to do
        System.out.println("About to match "  + noOfRegexps + " regexps from " + "'" + nameOfRegexpFile + "'" + " then match them against a bunch of files");
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
        regexps = allRegexps.subList(0, noOfRegexps - 1 );

        System.out.println("========");
        System.out.println("Run the native matcher");
        // Run the regex matcher
        MatcherBenchmarker.testACorpusNG(m, regexps, buf);

        // Run the java regex  matcher
        System.out.println("========");
        System.out.println("Run the java matcher");
        JavaRegexpMatcher jm = new JavaRegexpMatcher();
        MatcherBenchmarker.testACorpusNG(jm, regexps, buf);

        System.exit(0);
    }

    /**
     * No public constructor in an utility class.
     */
    private BenchmarkLargeCorpus() {
    }
}
