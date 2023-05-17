package no.rmz.rmatch.bugManifestations;

/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

/**
 * This test is intended to replicate the behavior that when running against the
 * dostoyevsky corpus, we don't seem to find any matches for "laden", which is weird.
 * This test is a mimimal replication of that error situation.
 */
@ExtendWith(MockitoExtension.class)
public class DenLadenAnomality {

    @Mock
    Action denAction;

    @Mock
    Action ladenAction;

    @Mock
    Action defaultAction;


    /**
     * Test matching the two regexps concurrently.
     */
    @Test
    public final void minimalReplicatingTest() throws RegexpParserException {

        // Prepare
        Matcher m = new MatcherImpl();

        List<String> regexps = new ArrayList<>();
        regexps.add("den");
        regexps.add("laden");
        regexps.add("ll");

        final String str = """
                lly
                drawn by heavy cart-horses and laden""";

        no.rmz.rmatch.interfaces.Buffer buffer = new no.rmz.rmatch.utils.StringBuffer(str);

        for (var r : regexps) {
            switch (r) {
                case "den":
                    m.add("den", denAction);
                    break;
                case "laden":
                    m.add("laden", ladenAction);
                    break;
                default:
                    m.add(r, defaultAction);
            }
        }

        // Act
        m.match(buffer);

        // Verify
        verify(denAction).performMatch(any(Buffer.class),   anyInt(), anyInt());
        verify(ladenAction).performMatch(any(Buffer.class), anyInt(), anyInt());

    }
}
