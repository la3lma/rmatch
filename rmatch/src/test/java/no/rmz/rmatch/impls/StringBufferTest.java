/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.utils.StringBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test that a StringBuffer has the length that it should and that
 * it contains the things it should contain.
 */
public final class StringBufferTest {

    /**
     * Treat it as a generic buffer.
     */
    private Buffer sb;

    /**
     * But in reality this is the string we're putting in there.
     */
    private String staticString;

    /**
     * Set up the test article.
     */
    @BeforeEach
    public void setUp() {
        staticString = "Fiskeboller";
        sb = new StringBuffer(staticString);
    }

    /**
     * Test that the length we get is the real one.
     */
    @Test
    public void testLength() {
        assertEquals(sb.getCurrentRestString().length(),
                staticString.length());
    }

    /**
     * Test that the string that is stored is the right one.
     */
    @Test
    public void testEquality() {
        assertEquals(sb.getCurrentRestString(), staticString);
    }
}
