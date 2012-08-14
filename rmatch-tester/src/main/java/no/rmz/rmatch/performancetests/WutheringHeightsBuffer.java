package no.rmz.rmatch.performancetests;

import no.rmz.rmatch.utils.StringBuffer;
import java.io.File;
import no.rmz.rmatch.interfaces.Buffer;

/**
 * A buffer implementation that delivers the full txt from Emily Bronte's novel
 * "Wuthering Heights".
 */
public final class WutheringHeightsBuffer implements Buffer, Cloneable {

    /**
     * The location in the local filesystem, relative to the regexpfilter
     * project, where the Wuthering Heights text is stored.
     */
    private static final String LOCATION_OF_WUTHERING_HEIGHTS =
            "corpus/wuthr10.txt";
    /**
     * A StringBuffer instance that holds the entire text from Emily Brontes
     * Wuthering Heights.
     */
    private final StringBuffer sb;

    /**
     * Create a new buffer and inhale the content from the file
     * corpus/wuthr10.txt.
     */
    public WutheringHeightsBuffer() {
        final FileInhaler fileReader =
                new FileInhaler(new File(LOCATION_OF_WUTHERING_HEIGHTS));
        sb = fileReader.inhaleAsStringBuffer();
    }

    /**
     * Set the position  to read the next character from to be i.
     * @param i  the next position to read from
     */
    public void setCurrentPos(final int i) {
        sb.setCurrentPos(i);
    }

    @Override
    public boolean hasNext() {
        return sb.hasNext();
    }

    @Override
    public String getString(final int start, final int stop) {
        return sb.getString(start, stop);
    }

    @Override
    public Character getNext() {
        return sb.getNext();
    }

    /**
     * Get the length of the buffer.
     * @return  length of buffer.
     */
    public int getLength() {
        return sb.getLength();
    }

    @Override
    public String getCurrentRestString() {
        return sb.getCurrentRestString();
    }

    /**
     * Get the substring from position 'pos' going on to
     * the end of the text.
     * @param pos the start position for the rest-string.
     * @return  a string.
     */
    public String getCurrentRestString(final int pos) {
        return sb.getCurrentRestString(pos);
    }

    @Override
    public int getCurrentPos() {
        return sb.getCurrentPos();
    }

    @Override
    public Buffer clone() {
        return sb.clone();
    }
}
