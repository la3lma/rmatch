package no.rmz.rmatch.bugManifestations;

/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.testutils.GraphDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This test is intended to replicate the behavior that when running against the dostoyevsky corpus,
 * we don't seem to find any matches for "laden," which is weird. This test is a minimal replication
 * of that error situation.
 */
@ExtendWith(MockitoExtension.class)
public class EdgeInvisibilityEnsurance {

  @Mock Action llAction;

  @Mock Action denAction;

  @Mock Action ladenAction;

  @Mock Action defaultAction;

  @Test
  public final void minimalReplicatingTest() throws RegexpParserException {

    // Prepare
    final String origString =
        """
                ll
                laden""";

    no.rmz.rmatch.interfaces.Buffer buffer = new no.rmz.rmatch.utils.StringBuffer(origString);

    Matcher m = new MatcherImpl();

    final List<String> regexps = new ArrayList<>();
    regexps.add("den");
    regexps.add("laden");
    regexps.add("ll");

    for (var r : regexps) {
      switch (r) {
        case "den":
          m.add("den", denAction);
          break;
        case "laden":
          m.add("laden", ladenAction);
          break;
        case "ll":
          m.add("ll", llAction);
          break;
        default:
          m.add(r, defaultAction);
      }
    }

    // Act
    m.match(buffer);

    // Document
    dumpGraph(m, 10);

    // Verify
    verify(denAction).performMatch(any(Buffer.class), anyInt(), anyInt());
    verify(llAction).performMatch(any(Buffer.class), anyInt(), anyInt());
    verify(ladenAction).performMatch(any(Buffer.class), anyInt(), anyInt());
    verify(defaultAction, times(0)).performMatch(any(Buffer.class), anyInt(), anyInt());
  }

  private static void dumpGraph(Matcher mi, int index) {
    try {
      GraphDumper.dump(
          mi.getNodeStorage(),
          new PrintStream(
              new File(String.format("graphs/ladenPostRunningNdfa-%d.gv", index)),
              StandardCharsets.UTF_8),
          new PrintStream(
              new File(String.format("graphs/ladenPostRunningDfa-%d.gv", index)),
              StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
