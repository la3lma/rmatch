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
package no.rmz.rmatch.engine.fastpath;

import static org.junit.jupiter.api.Assertions.*;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

/** Unit tests for StateSetBuffers. */
public class StateSetBuffersTest {

  @Test
  public void testGetInstance() {
    StateSetBuffers buffers1 = StateSetBuffers.get();
    assertNotNull(buffers1);

    // Should get the same instance on the same thread
    StateSetBuffers buffers2 = StateSetBuffers.get();
    assertSame(buffers1, buffers2);
  }

  @Test
  public void testEpsilonClosureBitSet() {
    StateSetBuffers buffers = StateSetBuffers.get();
    BitSet bitSet = buffers.getEpsilonClosureBitSet();

    assertNotNull(bitSet);
    assertTrue(bitSet.isEmpty(), "BitSet should be cleared");

    // Use it
    bitSet.set(5);
    bitSet.set(10);
    assertTrue(bitSet.get(5));
    assertTrue(bitSet.get(10));

    // Get it again - should be cleared
    BitSet bitSet2 = buffers.getEpsilonClosureBitSet();
    assertSame(bitSet, bitSet2, "Should be the same instance");
    assertTrue(bitSet2.isEmpty(), "BitSet should be cleared on retrieval");
  }

  @Test
  public void testNextStateBitSet() {
    StateSetBuffers buffers = StateSetBuffers.get();
    BitSet bitSet = buffers.getNextStateBitSet();

    assertNotNull(bitSet);
    assertTrue(bitSet.isEmpty(), "BitSet should be cleared");

    // Use it
    bitSet.set(1);
    bitSet.set(2);
    bitSet.set(3);

    // Get it again - should be cleared
    BitSet bitSet2 = buffers.getNextStateBitSet();
    assertTrue(bitSet2.isEmpty());
  }

  @Test
  public void testTempBitSet() {
    StateSetBuffers buffers = StateSetBuffers.get();
    BitSet bitSet = buffers.getTempBitSet();

    assertNotNull(bitSet);
    assertTrue(bitSet.isEmpty());

    bitSet.set(100);
    assertTrue(bitSet.get(100));

    BitSet bitSet2 = buffers.getTempBitSet();
    assertTrue(bitSet2.isEmpty());
  }

  @Test
  public void testStateIdArray() {
    StateSetBuffers buffers = StateSetBuffers.get();
    int[] array = buffers.getStateIdArray();

    assertNotNull(array);
    assertTrue(array.length >= buffers.getMaxSize());

    // Should get the same array instance each time
    int[] array2 = buffers.getStateIdArray();
    assertSame(array, array2);
  }

  @Test
  public void testClear() {
    StateSetBuffers buffers = StateSetBuffers.get();

    // Set some data
    BitSet eps = buffers.getEpsilonClosureBitSet();
    eps.set(5);

    BitSet next = buffers.getNextStateBitSet();
    next.set(10);

    BitSet temp = buffers.getTempBitSet();
    temp.set(15);

    // Clear all
    buffers.clear();

    // Verify all are cleared
    assertTrue(eps.isEmpty());
    assertTrue(next.isEmpty());
    assertTrue(temp.isEmpty());
  }

  @Test
  public void testThreadLocal() throws InterruptedException {
    StateSetBuffers mainThreadBuffers = StateSetBuffers.get();
    final StateSetBuffers[] otherThreadBuffers = new StateSetBuffers[1];

    Thread thread =
        new Thread(
            () -> {
              otherThreadBuffers[0] = StateSetBuffers.get();
            });

    thread.start();
    thread.join();

    assertNotNull(otherThreadBuffers[0]);
    assertNotSame(
        mainThreadBuffers, otherThreadBuffers[0], "Each thread should have its own instance");
  }

  @Test
  public void testMaxSize() {
    StateSetBuffers buffers = StateSetBuffers.get();
    int maxSize = buffers.getMaxSize();

    assertTrue(maxSize > 0);

    // Verify that BitSets can handle at least maxSize bits
    BitSet eps = buffers.getEpsilonClosureBitSet();
    eps.set(maxSize - 1);
    assertTrue(eps.get(maxSize - 1));

    // Verify array is at least maxSize
    int[] array = buffers.getStateIdArray();
    assertTrue(array.length >= maxSize);
  }
}
