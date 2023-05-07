package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.LookaheadBuffer;

public class LookaheadBufferImpl implements LookaheadBuffer {

    private final Buffer buffer;

    public LookaheadBufferImpl(final Buffer buffer) {
        this.buffer = buffer;
    }

    @Override
    @Deprecated
    public String getCurrentRestString() {
        return buffer.getCurrentRestString();
    }

    @Override
    public String getString(int start, int stop) {
        return buffer.getString(start, stop);
    }

    @Override
    public boolean hasNext() {
        return buffer.hasNext();
    }

    @Override
    public Character getNext() {
        return buffer.getNext();
    }

    @Override
    public int getCurrentPos() {
        return buffer.getCurrentPos();
    }

    @Override
    public Buffer clone() {
        return new LookaheadBufferImpl(buffer.clone());
    }
}
