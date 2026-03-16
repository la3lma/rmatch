package no.rmz.rmatch.benchmarks;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmark comparing different dispatch patterns to evaluate modern Java language features for
 * performance optimization.
 *
 * <p>This benchmark tests three optimization strategies suggested for improving RMatch performance:
 *
 * <ul>
 *   <li><b>Pattern Matching for instanceof:</b> Modern Java 16+ pattern matching vs traditional
 *       cast
 *   <li><b>Switch Expressions:</b> Enhanced switch with arrow syntax vs if-else chains
 *   <li><b>Character Classification:</b> Switch-based vs cascading if for code-point categories
 * </ul>
 *
 * <p>These benchmarks are inspired by the GC optimization experiments and aim to provide empirical
 * data on whether modern language features provide measurable performance benefits for regex engine
 * dispatch patterns.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(
    value = 1,
    jvmArgs = {"-Xms2G", "-Xmx2G", "-XX:+UseCompactObjectHeaders"})
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
public class DispatchOptimizationBench {

  // ========== Test Data Setup ==========

  @State(Scope.Benchmark)
  public static class TestData {
    public Object[] mixedObjects;
    public char[] testChars;
    public NodeType[] nodeTypes;

    @Setup
    public void setup() {
      // Create mixed object array for instanceof tests
      mixedObjects = new Object[1000];
      for (int i = 0; i < mixedObjects.length; i++) {
        int type = i % 5;
        mixedObjects[i] =
            switch (type) {
              case 0 -> new CharNodeMock('a');
              case 1 -> new CharRangeNodeMock('a', 'z');
              case 2 -> new AnyCharNodeMock();
              case 3 -> new TerminalNodeMock();
              default -> new FailNodeMock();
            };
      }

      // Create test characters for classification tests
      testChars = new char[1000];
      for (int i = 0; i < testChars.length; i++) {
        // Mix of ASCII and non-ASCII characters
        testChars[i] =
            switch (i % 10) {
              case 0, 1, 2 -> (char) ('a' + (i % 26)); // lowercase ASCII
              case 3, 4 -> (char) ('A' + (i % 26)); // uppercase ASCII
              case 5 -> (char) ('0' + (i % 10)); // digits
              case 6 -> ' '; // whitespace
              case 7 -> '.'; // punctuation
              case 8 -> (char) (0x00A0 + (i % 100)); // Latin-1 Supplement
              default -> (char) (0x4E00 + (i % 100)); // CJK characters
            };
      }

      // Create node types for sealed interface tests
      nodeTypes = new NodeType[1000];
      for (int i = 0; i < nodeTypes.length; i++) {
        nodeTypes[i] =
            switch (i % 5) {
              case 0 -> NodeType.CHAR;
              case 1 -> NodeType.CHAR_RANGE;
              case 2 -> NodeType.ANY_CHAR;
              case 3 -> NodeType.TERMINAL;
              default -> NodeType.FAIL;
            };
      }
    }
  }

  // ========== Mock Node Classes for instanceof Tests ==========

  abstract static class NodeMock {
    abstract String describe();
  }

  static final class CharNodeMock extends NodeMock {
    private final char ch;

    CharNodeMock(char ch) {
      this.ch = ch;
    }

    @Override
    String describe() {
      return "CharNode(" + ch + ")";
    }

    char getChar() {
      return ch;
    }
  }

  static final class CharRangeNodeMock extends NodeMock {
    private final char start;
    private final char end;

    CharRangeNodeMock(char start, char end) {
      this.start = start;
      this.end = end;
    }

    @Override
    String describe() {
      return "CharRangeNode(" + start + "-" + end + ")";
    }

    char getStart() {
      return start;
    }

    char getEnd() {
      return end;
    }
  }

  static final class AnyCharNodeMock extends NodeMock {
    @Override
    String describe() {
      return "AnyCharNode";
    }
  }

  static final class TerminalNodeMock extends NodeMock {
    @Override
    String describe() {
      return "TerminalNode";
    }
  }

  static final class FailNodeMock extends NodeMock {
    @Override
    String describe() {
      return "FailNode";
    }
  }

  // ========== Enum for sealed interface comparison ==========

  enum NodeType {
    CHAR,
    CHAR_RANGE,
    ANY_CHAR,
    TERMINAL,
    FAIL
  }

  // ========== Benchmark 1: Traditional instanceof with cast ==========

  @Benchmark
  public void traditionalInstanceofDispatch(TestData data, Blackhole bh) {
    for (Object obj : data.mixedObjects) {
      String result;
      if (obj instanceof CharNodeMock) {
        CharNodeMock node = (CharNodeMock) obj;
        result = "char:" + node.getChar();
      } else if (obj instanceof CharRangeNodeMock) {
        CharRangeNodeMock node = (CharRangeNodeMock) obj;
        result = "range:" + node.getStart() + "-" + node.getEnd();
      } else if (obj instanceof AnyCharNodeMock) {
        result = "anychar";
      } else if (obj instanceof TerminalNodeMock) {
        result = "terminal";
      } else if (obj instanceof FailNodeMock) {
        result = "fail";
      } else {
        result = "unknown";
      }
      bh.consume(result);
    }
  }

  // ========== Benchmark 2: Modern pattern matching for instanceof ==========

  @Benchmark
  public void patternMatchingInstanceofDispatch(TestData data, Blackhole bh) {
    for (Object obj : data.mixedObjects) {
      String result;
      if (obj instanceof CharNodeMock node) {
        result = "char:" + node.getChar();
      } else if (obj instanceof CharRangeNodeMock node) {
        result = "range:" + node.getStart() + "-" + node.getEnd();
      } else if (obj instanceof AnyCharNodeMock node) {
        result = "anychar";
      } else if (obj instanceof TerminalNodeMock node) {
        result = "terminal";
      } else if (obj instanceof FailNodeMock node) {
        result = "fail";
      } else {
        result = "unknown";
      }
      bh.consume(result);
    }
  }

  // ========== Benchmark 3: If-else chain for character classification ==========

  @Benchmark
  public void ifElseCharClassification(TestData data, Blackhole bh) {
    for (char ch : data.testChars) {
      CharCategory category;
      if (ch >= 'a' && ch <= 'z') {
        category = CharCategory.LOWERCASE_ASCII;
      } else if (ch >= 'A' && ch <= 'Z') {
        category = CharCategory.UPPERCASE_ASCII;
      } else if (ch >= '0' && ch <= '9') {
        category = CharCategory.DIGIT;
      } else if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
        category = CharCategory.WHITESPACE;
      } else if (ch < 128) {
        category = CharCategory.OTHER_ASCII;
      } else if (ch < 256) {
        category = CharCategory.LATIN1_SUPPLEMENT;
      } else {
        category = CharCategory.NON_ASCII;
      }
      bh.consume(category);
    }
  }

  // ========== Benchmark 4: Enhanced switch for character classification ==========

  @Benchmark
  public void switchCharClassification(TestData data, Blackhole bh) {
    for (char ch : data.testChars) {
      CharCategory category = classifyCharWithEnhancedSwitch(ch);
      bh.consume(category);
    }
  }

  private CharCategory classifyCharWithEnhancedSwitch(char ch) {
    // Use enhanced switch expressions for cleaner code
    if (ch >= 'a' && ch <= 'z') {
      return CharCategory.LOWERCASE_ASCII;
    } else if (ch >= 'A' && ch <= 'Z') {
      return CharCategory.UPPERCASE_ASCII;
    } else if (ch >= '0' && ch <= '9') {
      return CharCategory.DIGIT;
    }

    // Use enhanced switch for specific character matching
    return switch (ch) {
      case ' ', '\t', '\n', '\r' -> CharCategory.WHITESPACE;
      default -> {
        if (ch < 128) {
          yield CharCategory.OTHER_ASCII;
        } else if (ch < 256) {
          yield CharCategory.LATIN1_SUPPLEMENT;
        } else {
          yield CharCategory.NON_ASCII;
        }
      }
    };
  }

  // ========== Benchmark 5: Simple switch (range-based) for character classification ==========

  @Benchmark
  public void simpleSwitchCharClassification(TestData data, Blackhole bh) {
    for (char ch : data.testChars) {
      CharCategory category = classifyCharWithSimpleSwitch(ch);
      bh.consume(category);
    }
  }

  private CharCategory classifyCharWithSimpleSwitch(char ch) {
    // Use a simple switch that the compiler can optimize into a table
    if (ch >= 'a' && ch <= 'z') {
      return CharCategory.LOWERCASE_ASCII;
    } else if (ch >= 'A' && ch <= 'Z') {
      return CharCategory.UPPERCASE_ASCII;
    } else if (ch >= '0' && ch <= '9') {
      return CharCategory.DIGIT;
    }

    // Handle special cases with switch for better table generation
    return switch (ch) {
      case ' ', '\t', '\n', '\r' -> CharCategory.WHITESPACE;
      default -> {
        if (ch < 128) {
          yield CharCategory.OTHER_ASCII;
        } else if (ch < 256) {
          yield CharCategory.LATIN1_SUPPLEMENT;
        } else {
          yield CharCategory.NON_ASCII;
        }
      }
    };
  }

  // ========== Benchmark 6: Enum dispatch with traditional if-else ==========

  @Benchmark
  public void enumIfElseDispatch(TestData data, Blackhole bh) {
    for (NodeType type : data.nodeTypes) {
      String result;
      if (type == NodeType.CHAR) {
        result = "char processing";
      } else if (type == NodeType.CHAR_RANGE) {
        result = "range processing";
      } else if (type == NodeType.ANY_CHAR) {
        result = "anychar processing";
      } else if (type == NodeType.TERMINAL) {
        result = "terminal processing";
      } else if (type == NodeType.FAIL) {
        result = "fail processing";
      } else {
        result = "unknown";
      }
      bh.consume(result);
    }
  }

  // ========== Benchmark 7: Enum dispatch with enhanced switch ==========

  @Benchmark
  public void enumSwitchDispatch(TestData data, Blackhole bh) {
    for (NodeType type : data.nodeTypes) {
      String result =
          switch (type) {
            case CHAR -> "char processing";
            case CHAR_RANGE -> "range processing";
            case ANY_CHAR -> "anychar processing";
            case TERMINAL -> "terminal processing";
            case FAIL -> "fail processing";
          };
      bh.consume(result);
    }
  }

  // ========== Character category enum ==========

  enum CharCategory {
    LOWERCASE_ASCII,
    UPPERCASE_ASCII,
    DIGIT,
    WHITESPACE,
    OTHER_ASCII,
    LATIN1_SUPPLEMENT,
    NON_ASCII
  }
}
