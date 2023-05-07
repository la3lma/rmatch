package no.rmz.rmatch.interfaces;

public interface LookaheadBuffer extends Buffer {

    /**
     * Will return the character that will be read after the current one.
     * @return The next character to be read.
     */
    default Character peek() {
        return 'z'; // TODO: Obviously a placeholder, this shouldn't be default.
    }
}
