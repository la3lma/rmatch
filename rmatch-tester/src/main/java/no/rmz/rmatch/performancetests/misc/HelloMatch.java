package no.rmz.rmatch.performancetests.misc;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.StringSourceBuffer;


import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A short & sweet example of how to use the rmatch matcher
 * library.
 */
public final  class HelloMatch {

    /**
     * Errors can occur, and when they do we log them.
     */
    private static final  Logger LOG =
            Logger.getLogger(HelloMatch.class.getName());

    /**
     * This is a utility class so no constructor for you.
     */
    private HelloMatch() {
    }

    /**
     * A really simple little demo program.
     * @param argv we ignore command line params.
     */
    public static void main(final String [] argv) {
        try {

            // The input we will match in is a simple string
            final StringSourceBuffer sb = new StringSourceBuffer("hello world");

            // We get the matcher engine in the canonical way.
            final Matcher       m = MatcherFactory.newMatcher();

            // When a match is found, we will run this action which
            // will just print out the word.
            final Action        a = new Action() {

                @Override
                public void performMatch(
                        final Buffer b,
                        final int start,
                        final int end) {
                    System.out.println("Match found: '"
                            + b.getString(start, end + 1)
                            + "'");
                }
            };

            // Set up a couple of triggers. The first param is
            // a regular expression, and the second is an action that
            // is triggered whenever the trigger matches the input.
            m.add("hello", a);
            m.add("world", a);

            // Then run the input through the matcher, triggering matches.
            m.match(sb);
        } catch (RegexpParserException ex) {
            LOG.log(Level.SEVERE, "Parsing regular expressions failed", ex);
        }
    }
}
