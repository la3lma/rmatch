package no.rmz.rmatch.interfaces;

public interface LookaheadBuffer extends Buffer {

    /**
     * Will return the character that will be read after the current one.
     * @return The next character to be read. If no more characters to be read
     * then return null.
     */
    Character peek();
}
