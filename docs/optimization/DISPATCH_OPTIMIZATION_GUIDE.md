# Dispatch Optimization Implementation Guide

This guide provides practical recommendations for applying the findings from the dispatch optimization benchmarks to the RMatch codebase.

## Quick Reference

| Optimization | Performance Impact | Recommendation | Apply To |
|--------------|-------------------|----------------|----------|
| Pattern matching instanceof | 0.08% (none) | Use for readability only | New code |
| Enum switch expressions | +13.6% | **RECOMMENDED** | Hot paths with enum dispatch |
| If-else char ranges | +19.5% vs switch | **Keep if-else** | Character classification |

## 1. Enum Switch Expressions: APPLY

### When to Apply

Convert enum if-else chains to switch expressions when:
- The code is in a hot path (profiling shows significant time spent)
- There are 3+ enum comparisons in a chain
- The enum has a stable set of values

### Code Pattern to Find

```bash
# Search for enum if-else patterns
grep -r "if.*==.*\." rmatch/src/main/java | grep -E "enum|Type"
```

### Example Refactoring

**Before:**
```java
public String process(NodeType type) {
    String result;
    if (type == NodeType.CHAR) {
        result = processChar();
    } else if (type == NodeType.CHAR_RANGE) {
        result = processRange();
    } else if (type == NodeType.ANY_CHAR) {
        result = processAny();
    } else if (type == NodeType.TERMINAL) {
        result = processTerminal();
    } else {
        result = processUnknown();
    }
    return result;
}
```

**After:**
```java
public String process(NodeType type) {
    return switch (type) {
        case CHAR -> processChar();
        case CHAR_RANGE -> processRange();
        case ANY_CHAR -> processAny();
        case TERMINAL -> processTerminal();
        default -> processUnknown();
    };
}
```

### Benefits

- 13.6% faster execution
- More maintainable (exhaustiveness checking)
- Less error-prone (no missing cases)
- Cleaner code (expression vs statement)

## 2. Pattern Matching instanceof: OPTIONAL

### When to Use

Use pattern matching for instanceof for **code readability**, not performance:
- New code where readability is important
- Complex type checks with multiple casts
- Code that will be reviewed by others

### Do NOT Refactor Existing Code

The benchmarks show **zero performance benefit** (0.08% difference). Do not spend time refactoring existing instanceof patterns.

### Example Usage

**Use pattern matching in NEW code:**
```java
// Good: cleaner, more readable
if (buffer instanceof StringBuffer sb) {
    return sb.getUnderlyingString();
} else if (buffer instanceof ByteBuffer bb) {
    return convertToString(bb);
}
```

**Keep traditional in EXISTING code:**
```java
// Don't refactor: no performance benefit
if (buffer instanceof StringBuffer) {
    StringBuffer sb = (StringBuffer) buffer;
    return sb.getUnderlyingString();
}
```

## 3. Character Classification: Keep If-Else

### Do NOT Convert to Switch

Character range checks should **remain as if-else chains**:
- 19.5% faster than switch expressions
- JVM optimizes range comparisons better
- Better branch prediction for sequential checks

### Keep This Pattern

```java
// CORRECT: Fast path for ASCII
if (ch >= 'a' && ch <= 'z') {
    return CharCategory.LOWERCASE_ASCII;
} else if (ch >= 'A' && ch <= 'Z') {
    return CharCategory.UPPERCASE_ASCII;
} else if (ch >= '0' && ch <= '9') {
    return CharCategory.DIGIT;
} else if (ch < 128) {
    return CharCategory.OTHER_ASCII;
} else {
    return CharCategory.NON_ASCII;
}
```

### Don't Do This

```java
// SLOWER: Don't use switch for range checks
switch (ch) {
    case char c when (c >= 'a' && c <= 'z') -> CharCategory.LOWERCASE_ASCII;
    case char c when (c >= 'A' && c <= 'Z') -> CharCategory.UPPERCASE_ASCII;
    // ... this is 19.5% slower!
}
```

## 4. Finding Optimization Opportunities

### Profile Hot Paths

Before applying optimizations, profile to find hot paths:

```bash
# Run profiler (example with async-profiler)
make profile

# Or use JMH profilers
java -jar benchmarks.jar -prof perfnorm YourBenchmark
```

### Search for Patterns

```bash
# Find enum if-else chains (candidates for switch)
grep -r "if.*==.*\." rmatch/src/main/java

# Find instanceof patterns (document why not optimizing)
grep -r "instanceof" rmatch/src/main/java

# Find character classification (verify using if-else)
grep -r "ch >=" rmatch/src/main/java
```

## 5. Validation

After applying optimizations:

1. **Run benchmarks** to verify improvement:
```bash
make bench-dispatch
```

2. **Run unit tests** to ensure correctness:
```bash
mvn test
```

3. **Check performance** on representative workloads:
```bash
make bench-macro
```

## Current Status

As of the initial analysis:

### ✅ Already Optimal
- Character classification in `SurfaceRegexpParser.java` uses if-else (correct)
- instanceof patterns already use modern syntax where beneficial

### 🔍 Potential Opportunities
- Profile the codebase to identify hot paths with enum dispatch
- Look for enum if-else chains in `MatchEngineImpl`, node processing, and dispatch logic
- Measure actual impact before and after changes

### ❌ Don't Optimize
- Don't refactor instanceof for performance (no benefit)
- Don't convert character range checks to switch (slower)
- Don't optimize cold paths (premature optimization)

## Summary

**High-impact optimization:** Convert enum if-else chains to switch expressions in hot paths (13.6% faster)

**No benefit:** Pattern matching for instanceof (use for readability only)

**Keep current:** If-else for character classification (19.5% faster than switch)

**Next steps:**
1. Profile to find hot paths
2. Identify enum if-else chains in hot paths
3. Apply switch expression optimization
4. Verify with benchmarks
5. Document changes

See [DISPATCH_OPTIMIZATION_RESULTS.md](DISPATCH_OPTIMIZATION_RESULTS.md) for detailed benchmark results and analysis.
