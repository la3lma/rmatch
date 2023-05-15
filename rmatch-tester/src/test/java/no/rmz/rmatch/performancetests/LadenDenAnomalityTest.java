/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.performancetests;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static no.rmz.rmatch.performancetests.BenchmarkLargeCorpus.getStringBuilderFromFileContent;
import static no.rmz.rmatch.performancetests.BenchmarkLargeCorpus.readRegexpsFromFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * This test is intended to replicate the behavior that when running against the
 * dostoyevsky corpus, we don't seem to find any matches for "laden", which is weird.
 */
@ExtendWith(MockitoExtension.class)
public class LadenDenAnomalityTest {

    /**
     * Mocked action. Used to check that matches are found
     * in the right locations.
     */
    @Mock
    Action denAction;

    @Mock
    Action ladenAction;

    @Mock
    Action defaultAction;


    /**
     * A test article, the matcher implementation.
     */
    private Matcher m;

    /**
     * Instantiate test articles and set up the compiler mock
     * to simulate proper compilation of "ab" and "ac".
     */
    @BeforeEach
    public void setUp() {
        m = new MatcherImpl();
    }

    /**
     * Test matching the two regexps concurrently.
     */
    @Test
    public final void testUseOfOrdinaryMatcherOn1500SubsetOfRegexps() throws RegexpParserException {

        // Prepare
        final String[] corpusPaths = new String[]{"corpus/crime-and-punishment-by-dostoyevsky.txt"};
        final String corpus = getStringBuilderFromFileContent(corpusPaths).toString();
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils
                .StringBuffer(corpus);
        final List<String> regexps = readRegexpsFromFile("corpus/unique-words.txt", 1500);

        for (var r : regexps) {
            switch (r) {
                case "den":  m.add("den", denAction); break;
                case "laden":  m.add("laden", ladenAction); break;
                default:  m.add(r, defaultAction);
            }
        }

        // Act
        m.match(b);

        // Verify
        verify(ladenAction).performMatch(any(Buffer.class), eq(128310), eq(128314));
        verify(denAction).performMatch(any(Buffer.class),   eq(128312), eq(128314));
    }
}
