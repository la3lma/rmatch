package no.rmz.rmatch.performancetests;


import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test reading the text of wuthering heights.
 */
public final class WutheringHeightsBufferTest {

    /**
     * The number of characters in the text.
     */
    private static final int
            MINIMUM_NUMBER_OF_CHARS_IN_WUTHERING_HEIGHTS = 662077;

    /**
     * Test reading the text and then check that we got all the
     * characters.
     */
    @Test
    public void testInhale() {
        final WutheringHeightsBuffer whb = new WutheringHeightsBuffer();
        final int len = whb.getLength();

        assertEquals(MINIMUM_NUMBER_OF_CHARS_IN_WUTHERING_HEIGHTS, len);
    }
}
