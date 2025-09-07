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
package no.rmz.rmatch.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the FastCounter and FastCounters system. */
public class FastCounterTest {

  public FastCounterTest() {}

  @BeforeAll
  public static void setUpClass() {}

  @AfterAll
  public static void tearDownClass() {}

  @BeforeEach
  public void setUp() {}

  @AfterEach
  public void tearDown() {}

  /** Test basic increment functionality. */
  @Test
  public void testIncrement() {
    final FastCounter counter = FastCounters.newCounter(CounterType.MATCH_IMPL);
    final long initialValue = counter.get();
    final long result = counter.inc();
    assertEquals(initialValue + 1, result);
    assertEquals(result, counter.get());
  }

  /** Test basic decrement functionality. */
  @Test
  public void testDecrement() {
    final FastCounter counter = FastCounters.newCounter(CounterType.MATCH_IMPL);
    counter.inc(); // Set to 1
    final long initialValue = counter.get();
    final long result = counter.dec();
    assertEquals(initialValue - 1, result);
    assertEquals(result, counter.get());
  }

  /** Test thread safety with multiple threads incrementing the same counter type. */
  @Test
  public void testMultiThreadedCounterAccess() throws InterruptedException {
    final FastCounter counter = FastCounters.newCounter(CounterType.DFA_NODE_IMPL);
    final int noOfThreadsInPool = 10;
    final int noOfIterationsInRunnable = 1000;
    final int noOfRunnables = noOfThreadsInPool;
    final int noOfIncrements = noOfRunnables * noOfIterationsInRunnable;
    final ExecutorService executors = Executors.newFixedThreadPool(noOfThreadsInPool);

    // Get initial value before creating threads
    final long initialValue = counter.get();

    try {
      final Collection<Callable<Object>> runnables = new ArrayList<>();

      for (int j = 0; j < noOfRunnables; j++) {
        final Callable<Object> runnable =
            () -> {
              for (int i = 0; i < noOfIterationsInRunnable; i++) {
                counter.inc();
              }
              return null;
            };
        runnables.add(runnable);
      }

      executors.invokeAll(runnables);
    } finally {
      executors.shutdown();
    }

    // Verify the final count
    assertEquals(initialValue + noOfIncrements, counter.get());
  }

  /** Test that different counter types maintain separate values. */
  @Test
  public void testSeparateCounterTypes() {
    final FastCounter matchCounter = FastCounters.newCounter(CounterType.MATCH_IMPL);
    final FastCounter dfaCounter = FastCounters.newCounter(CounterType.DFA_NODE_IMPL);
    final FastCounter ndfaCounter = FastCounters.newCounter(CounterType.ABSTRACT_NDFA_NODES);

    final long matchInitial = matchCounter.get();
    final long dfaInitial = dfaCounter.get();
    final long ndfaInitial = ndfaCounter.get();

    matchCounter.inc();
    matchCounter.inc();
    dfaCounter.inc();

    assertEquals(matchInitial + 2, matchCounter.get());
    assertEquals(dfaInitial + 1, dfaCounter.get());
    assertEquals(ndfaInitial, ndfaCounter.get()); // Should remain unchanged
  }

  /** Test the toString method returns properly formatted output. */
  @Test
  public void testToString() {
    final FastCounter counter = FastCounters.newCounter(CounterType.MATCH_SET_IMPL);
    counter.inc();
    counter.inc();
    counter.inc();
    final String result = counter.toString();
    assertTrue(result.startsWith("#'MatchSetImpl'="));
    assertTrue(result.endsWith("3"));
  }

  /** Test compatibility with legacy Counter system via getCounters(). */
  @Test
  public void testLegacyCompatibility() {
    final FastCounter fastCounter = FastCounters.newCounter(CounterType.KNOWN_DFA_EDGES);
    fastCounter.inc();
    fastCounter.inc();
    fastCounter.inc();
    fastCounter.inc();
    fastCounter.inc();

    final Collection<Counter> legacyCounters = FastCounters.getCounters();
    assertFalse(legacyCounters.isEmpty());

    // Find our counter in the collection
    boolean found = false;
    for (final Counter c : legacyCounters) {
      if (c.toString().contains("Known DFA Edges")) {
        found = true;
        assertTrue(c.toString().contains("5"));
        break;
      }
    }
    assertTrue(found, "Should find the Known DFA Edges counter in the collection");
  }

  /** Test that getCounters returns all counter types. */
  @Test
  public void testGetCountersCompleteness() {
    final Collection<Counter> counters = FastCounters.getCounters();
    assertEquals(
        CounterType.values().length, counters.size(), "Should have all counter types represented");
  }

  /** Verify counter names match expected legacy names. */
  @Test
  public void testCounterNames() {
    assertEquals("AbstractNDFANodes", CounterType.ABSTRACT_NDFA_NODES.getLegacyName());
    assertEquals("Cached edges going to an NDFA.", CounterType.CACHED_NDFA_EDGES.getLegacyName());
    assertEquals("DFANodeImpl", CounterType.DFA_NODE_IMPL.getLegacyName());
    assertEquals("Known DFA Edges", CounterType.KNOWN_DFA_EDGES.getLegacyName());
    assertEquals("MatchImpl", CounterType.MATCH_IMPL.getLegacyName());
    assertEquals("MatchSetImpl", CounterType.MATCH_SET_IMPL.getLegacyName());
  }

  /** Test that multiple FastCounter instances of the same type share state. */
  @Test
  public void testSharedState() {
    final FastCounter counter1 = FastCounters.newCounter(CounterType.CACHED_NDFA_EDGES);
    final FastCounter counter2 = FastCounters.newCounter(CounterType.CACHED_NDFA_EDGES);

    final long initial = counter1.get();
    counter1.inc();
    assertEquals(initial + 1, counter2.get());

    counter2.inc();
    assertEquals(initial + 2, counter1.get());
  }
}
