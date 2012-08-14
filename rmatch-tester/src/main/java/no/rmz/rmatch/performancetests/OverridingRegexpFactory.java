package no.rmz.rmatch.performancetests;

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.Map;
import java.util.Map.Entry;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * A regexp factory used to inject mocked compiled result into a context
 * resembling a real production environment.
 */
public final class OverridingRegexpFactory implements RegexpFactory {

    /**
     * Mapping regexp to moced compilationre results.
     */
    private final Map<Regexp, NDFANode> mockedCompilationResult;
    /**
     * If true a fallthrough to the default regexp factory will be enabled for
     * exprs that are not found in the mockedCompilationResult map.
     */
    private static final boolean FALL_THROUGH_IS_ENABLED = false;

    /**
     * Instantiate a new compilation factory where results can be mocked by
     * inserting into the map.
     *
     * @param mockedCompilationResult A map containing the compilation results
     *        this instance should deliver.
     */
    public OverridingRegexpFactory(
            final Map<Regexp, NDFANode> mockedCompilationResult) {
        checkNotNull(mockedCompilationResult);
        this.mockedCompilationResult = mockedCompilationResult;
    }

    // Potentially Horribly inefficient linear search
    @Override
    public Regexp newRegexp(final String regexpString) {
        for (final Entry<Regexp, NDFANode> entry
                : mockedCompilationResult.entrySet()) {
            final Regexp key = entry.getKey();
            final String keyString = key.getRexpString();
            if (keyString.equals(regexpString)) {
                return key;
            }
        }
        if (FALL_THROUGH_IS_ENABLED) {
            return RegexpFactory.DEFAULT_REGEXP_FACTORY.newRegexp(regexpString);
        } else {
            throw new IllegalArgumentException(
                    "Attempt to call newRegexp on unkonwn regexp: "
                    + regexpString);
        }
    }
}
