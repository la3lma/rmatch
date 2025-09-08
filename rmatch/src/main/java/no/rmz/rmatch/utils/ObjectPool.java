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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * A simple object pool to reduce allocation overhead for frequently created objects.
 *
 * @param <T> The type of objects to pool
 */
public final class ObjectPool<T> {

  private final Deque<T> pool = new ArrayDeque<>();
  private final Supplier<T> factory;
  private final int maxSize;

  /**
   * Creates an object pool with the given factory and maximum size.
   *
   * @param factory A supplier that creates new instances when the pool is empty
   * @param maxSize Maximum number of objects to keep in the pool
   */
  public ObjectPool(final Supplier<T> factory, final int maxSize) {
    this.factory = factory;
    this.maxSize = maxSize;
  }

  /**
   * Gets an object from the pool, creating a new one if the pool is empty.
   *
   * @return An object from the pool or a newly created one
   */
  public T get() {
    final T pooled = pool.pollLast();
    return pooled != null ? pooled : factory.get();
  }

  /**
   * Returns an object to the pool for reuse.
   *
   * @param obj The object to return to the pool
   */
  public void release(final T obj) {
    if (obj != null && pool.size() < maxSize) {
      pool.addLast(obj);
    }
  }

  /**
   * Gets the current size of the pool.
   *
   * @return The number of objects currently in the pool
   */
  public int size() {
    return pool.size();
  }

  /** Clears all objects from the pool. */
  public void clear() {
    pool.clear();
  }
}
