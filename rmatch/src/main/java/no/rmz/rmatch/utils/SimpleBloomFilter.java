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

import java.util.BitSet;

/**
 * A simple Bloom filter implementation for fast set membership testing. Provides fast negative
 * lookups with no false negatives, but may have false positives.
 */
public final class SimpleBloomFilter {
  private final BitSet bitSet;
  private final int numHashFunctions;
  private final int bitSetSize;

  /**
   * Create a new Bloom filter.
   *
   * @param expectedElements Expected number of elements to be added
   * @param falsePositiveRate Desired false positive rate (e.g., 0.01 for 1%)
   */
  public SimpleBloomFilter(final int expectedElements, final double falsePositiveRate) {
    this.bitSetSize = optimalBitSetSize(expectedElements, falsePositiveRate);
    this.numHashFunctions = optimalNumHashFunctions(bitSetSize, expectedElements);
    this.bitSet = new BitSet(bitSetSize);
  }

  /**
   * Add a string to the Bloom filter.
   *
   * @param item the string to add
   */
  public void put(final String item) {
    if (item == null) {
      return;
    }

    final int hash1 = item.hashCode();
    final int hash2 = hash1 >>> 16;

    for (int i = 0; i < numHashFunctions; i++) {
      int hash = hash1 + (i * hash2);
      if (hash < 0) {
        hash = ~hash;
      }
      bitSet.set(hash % bitSetSize);
    }
  }

  /**
   * Test if a string might be in the set.
   *
   * @param item the string to test
   * @return false if definitely not in set, true if might be in set
   */
  public boolean mightContain(final String item) {
    if (item == null) {
      return false;
    }

    final int hash1 = item.hashCode();
    final int hash2 = hash1 >>> 16;

    for (int i = 0; i < numHashFunctions; i++) {
      int hash = hash1 + (i * hash2);
      if (hash < 0) {
        hash = ~hash;
      }
      if (!bitSet.get(hash % bitSetSize)) {
        return false;
      }
    }
    return true;
  }

  private static int optimalBitSetSize(final int expectedElements, final double falsePositiveRate) {
    final double rate = (falsePositiveRate == 0) ? Double.MIN_VALUE : falsePositiveRate;
    return (int) (-expectedElements * Math.log(rate) / (Math.log(2) * Math.log(2)));
  }

  private static int optimalNumHashFunctions(final int bitSetSize, final int expectedElements) {
    return Math.max(1, (int) Math.round((double) bitSetSize / expectedElements * Math.log(2)));
  }
}
