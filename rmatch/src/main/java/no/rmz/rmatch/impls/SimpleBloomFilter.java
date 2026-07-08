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
package no.rmz.rmatch.impls;

import java.util.BitSet;

/**
 * Simple Bloom filter for fast string membership pre-screening.
 *
 * <p>Bloom filters never produce false negatives: if {@link #mightContain(String)} returns {@code
 * false}, the item was not added. They may produce false positives, so {@code true} means "possibly
 * present," not "definitely present."
 */
final class SimpleBloomFilter {
  private final BitSet bitSet;
  private final int numHashFunctions;
  private final int bitSetSize;

  /**
   * Create a Bloom filter sized for the expected workload.
   *
   * @param expectedElements expected number of inserted elements
   * @param falsePositiveRate desired false-positive rate, for example {@code 0.01} for one percent
   */
  SimpleBloomFilter(final int expectedElements, final double falsePositiveRate) {
    this.bitSetSize = optimalBitSetSize(expectedElements, falsePositiveRate);
    this.numHashFunctions = optimalNumHashFunctions(bitSetSize, expectedElements);
    this.bitSet = new BitSet(bitSetSize);
  }

  /**
   * Add a string to the filter.
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
   * Return whether a string might have been added.
   *
   * @param item the string to test
   * @return {@code false} if definitely absent; {@code true} if possibly present
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
