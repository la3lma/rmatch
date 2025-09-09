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

import static org.junit.jupiter.api.Assertions.assertEquals;

import no.rmz.rmatch.interfaces.LookaheadBuffer;
import no.rmz.rmatch.utils.StringBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test that a StringBuffer has the length that it should and that it contains the things it should
 * contain.
 */
public final class LookaheadStringBufferTest {

  /** Treat it as a generic buffer. */
  private LookaheadBuffer lsb;

  /** But in reality this is the string we're putting in there. */
  private String staticString;

  /** Set up the test article. */
  @BeforeEach
  public void setUp() {
    staticString = "Fiskeboller";
    lsb = new LookaheadBufferImpl(new StringBuffer(staticString));
  }

  /** Test that the length we get is the real one. */
  @Test
  public void testLength() {
    //noinspection deprecation
    assertEquals(lsb.getCurrentRestString().length(), staticString.length());
  }

  /** Test that the string that is stored is the right one. */
  @Test
  public void testEquality() {
    //noinspection deprecation
    assertEquals(lsb.getCurrentRestString(), staticString);
  }

  /** Test that the length we get is the real one. */
  @Test
  public void testPeek() {
    assertEquals('F', lsb.peek());
    assertEquals('F', lsb.peek()); // Idempotence
    assertEquals('F', lsb.getNext());
    assertEquals('i', lsb.peek());
    assertEquals('i', lsb.peek());
    assertEquals('i', lsb.getNext());
    assertEquals('s', lsb.getNext());
    assertEquals('k', lsb.getNext());
    assertEquals('e', lsb.getNext());
    assertEquals('b', lsb.getNext());
    assertEquals('o', lsb.getNext());
    assertEquals('l', lsb.getNext());
    assertEquals('l', lsb.getNext());
    assertEquals('e', lsb.getNext());
    assertEquals('r', lsb.getNext());
    assertEquals(null, lsb.peek());
  }
}
