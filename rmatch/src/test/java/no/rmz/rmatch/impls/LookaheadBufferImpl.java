package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.LookaheadBuffer;

public class LookaheadBufferImpl implements LookaheadBuffer, Cloneable {

  private final Object lock = new Object();
  private final Buffer buffer;
  private Character peekedChar;
  private boolean havePeeked;

  public LookaheadBufferImpl(final Buffer buffer) {
    this.buffer = buffer;
    this.havePeeked = false;
    this.peekedChar = null;
  }

  @Override
  @Deprecated
  public String getCurrentRestString() {
    synchronized (lock) {
      return buffer.getCurrentRestString();
    }
  }

  @Override
  public String getString(int start, int stop) {
    synchronized (lock) {
      return buffer.getString(start, stop);
    }
  }

  @Override
  public boolean hasNext() {
    synchronized (lock) {
      return buffer.hasNext();
    }
  }

  @Override
  public Character getNext() {
    synchronized (lock) {
      if (havePeeked) {
        havePeeked = false;
        return peekedChar;
      } else {
        return buffer.getNext();
      }
    }
  }

  @Override
  public int getCurrentPos() {
    synchronized (lock) {
      return buffer.getCurrentPos();
    }
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Buffer clone() {
    synchronized (lock) {
      return new LookaheadBufferImpl(buffer.clone());
    }
  }

  @Override
  public Character peek() {
    synchronized (lock) {
      if (!this.havePeeked) {
        this.havePeeked = true;
        if (buffer.hasNext()) {
          this.peekedChar = buffer.getNext();
        } else {
          this.peekedChar = null;
        }
      }
      return peekedChar;
    }
  }
}
