package no.rmz.rmatch.internal;

/**
 * Minimal precondition helpers used internally to avoid exposing a larger utility dependency from
 * the library artifact.
 */
public final class Checks {
  private Checks() {}

  public static <T> T checkNotNull(final T reference) {
    if (reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }

  public static <T> T checkNotNull(final T reference, final Object message) {
    if (reference == null) {
      throw new NullPointerException(String.valueOf(message));
    }
    return reference;
  }

  public static void checkArgument(final boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  public static void checkArgument(final boolean expression, final Object message) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(message));
    }
  }

  public static void checkState(final boolean expression) {
    if (!expression) {
      throw new IllegalStateException();
    }
  }

  public static void checkState(final boolean expression, final Object message) {
    if (!expression) {
      throw new IllegalStateException(String.valueOf(message));
    }
  }
}
