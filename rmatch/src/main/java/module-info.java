/**
 * Public rmatch module.
 *
 * <p>The exported package {@code no.rmz.rmatch} contains the supported application-facing API.
 * Implementation, compiler, engine, and diagnostic packages are intentionally not exported so they
 * can evolve before the stable 2.0 line without becoming part of the compatibility contract.
 */
module no.rmz.rmatch {
  requires java.logging;
  requires java.management;

  exports no.rmz.rmatch;
}
