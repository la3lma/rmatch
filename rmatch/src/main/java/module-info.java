module no.rmz.rmatch {
  requires java.logging;
  requires java.management;
  requires ahocorasick;

  exports no.rmz.rmatch.compiler;
  exports no.rmz.rmatch.impls;
  exports no.rmz.rmatch.interfaces;
  exports no.rmz.rmatch.utils;
}
