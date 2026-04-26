# Java File Critique (2026-03-16)

Static heuristic over 140 Java files. Scoring weights:
- likely unused file (main code): very high
- likely unused members: high
- low reference count: medium
- style/perf hints: low

JUnit test classes are not treated as dead code when unreferenced.

## Ranked Badness

| Rank | Score | Refs | File | Signals |
|---:|---:|---:|---|---|
| 1 | 100 | 0 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/JavaRegexpTester.java` | unused-file |
| 2 | 25 | 2 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/SystemInfo.java` | low-usage, style |
| 3 | 25 | 2 | `rmatch/src/main/java/no/rmz/rmatch/impls/BloomFilterMatchEngine.java` | low-usage, style |
| 4 | 22 | 52 | `rmatch/src/main/java/no/rmz/rmatch/impls/MatchSetImpl.java` | style, unused-members |
| 5 | 20 | 2 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BenchmarkTheWutheringHeightsCorpus.java` | low-usage |
| 6 | 20 | 2 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/MatchPairAnalysis.java` | low-usage |
| 7 | 20 | 1 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/TestJavaRegexpUnfairly.java` | low-usage |
| 8 | 20 | 1 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/Collector.java` | low-usage |
| 9 | 20 | 2 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/MatchDetector.java` | low-usage |
| 10 | 20 | 1 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/NormalizationBenchmark.java` | low-usage |
| 11 | 20 | 2 | `rmatch/src/main/java/no/rmz/rmatch/compiler/AlternativesBuilder.java` | low-usage |
| 12 | 20 | 1 | `rmatch/src/main/java/no/rmz/rmatch/compiler/AnyCharNode.java` | low-usage |
| 13 | 20 | 1 | `rmatch/src/main/java/no/rmz/rmatch/compiler/CharRangeNode.java` | low-usage |
| 14 | 20 | 2 | `rmatch/src/main/java/no/rmz/rmatch/compiler/CharSetBuilder.java` | low-usage |
| 15 | 20 | 1 | `rmatch/src/main/java/no/rmz/rmatch/compiler/FailNode.java` | low-usage |
| 16 | 20 | 2 | `rmatch/src/main/java/no/rmz/rmatch/compiler/StringSource.java` | low-usage |
| 17 | 20 | 1 | `rmatch/src/main/java/no/rmz/rmatch/impls/MultiMatcher.java` | low-usage |
| 18 | 20 | 2 | `rmatch/src/main/java/no/rmz/rmatch/impls/RegexpStorageImpl.java` | low-usage |
| 19 | 20 | 1 | `rmatch/src/test/java/no/rmz/rmatch/impls/LookaheadBufferImpl.java` | low-usage |
| 20 | 20 | 1 | `rmatch/src/test/java/no/rmz/rmatch/integrationtests/AlternativeCharsNode.java` | low-usage |
| 21 | 14 | 0 | `rmatch/src/test/java/no/rmz/rmatch/ordinaryuse/OrdinaryUsageSmokeTest.java` | unused-members |
| 22 | 12 | 6 | `rmatch/src/main/java/no/rmz/rmatch/impls/StartNode.java` | unused-members |
| 23 | 10 | 0 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/GitHubActionPerformanceTestRunner.java` | entrypoint, style |
| 24 | 8 | 0 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BenchmarkLargeCorpus.java` | entrypoint, style |
| 25 | 8 | 38 | `rmatch/src/main/java/no/rmz/rmatch/impls/RegexpImpl.java` | style |
| 26 | 8 | 16 | `rmatch/src/test/java/no/rmz/rmatch/testutils/GraphDumper.java` | style |
| 27 | 5 | 25 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BaselineManager.java` | style |
| 28 | 5 | 0 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BasicPerformanceTest.java` | entrypoint |
| 29 | 5 | 0 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BenchmarkTheWutheringHeightsCorpusWithMemory.java` | entrypoint |
| 30 | 5 | 24 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/ComprehensivePerformanceTest.java` | style |
| 31 | 5 | 11 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/PerformanceCriteriaEvaluator.java` | style |
| 32 | 5 | 0 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/TestJavaRegexpUnfairlyWithMemory.java` | entrypoint |
| 33 | 5 | 86 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/MatcherBenchmarker.java` | style |
| 34 | 5 | 0 | `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/SequenceLoaderTest.java` | style |
| 35 | 5 | 36 | `rmatch/src/main/java/no/rmz/rmatch/abstracts/AbstractNDFANode.java` | style |
| 36 | 5 | 19 | `rmatch/src/main/java/no/rmz/rmatch/engine/prefilter/LiteralPrefilter.java` | style |
| 37 | 5 | 32 | `rmatch/src/main/java/no/rmz/rmatch/impls/DFANodeImpl.java` | style |
| 38 | 5 | 10 | `rmatch/src/main/java/no/rmz/rmatch/impls/FastPathMatchEngine.java` | style |
| 39 | 5 | 21 | `rmatch/src/main/java/no/rmz/rmatch/impls/MatchEngineImpl.java` | style |
| 40 | 5 | 0 | `rmatch/src/test/java/no/rmz/rmatch/compiler/ARegexpCompilerTest.java` | style |
| 41 | 3 | 31 | `rmatch/src/main/java/no/rmz/rmatch/impls/MatchImpl.java` | style |
| 42 | 3 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/DominationHeapTest.java` | style |
| 43 | 0 | 13 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/GitHubActionPerformanceTest.java` | clean |
| 44 | 0 | 7 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/JavaRegexpMatcher.java` | clean |
| 45 | 0 | 12 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/FileInhaler.java` | clean |
| 46 | 0 | 16 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/GraphDumper.java` | clean |
| 47 | 0 | 5 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/LineMatcher.java` | clean |
| 48 | 0 | 3 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/LineSource.java` | clean |
| 49 | 0 | 4 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/MatcherBenchmarkerWithMemory.java` | clean |
| 50 | 0 | 9 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/StringSourceBuffer.java` | clean |
| 51 | 0 | 20 | `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/WutheringHeightsBuffer.java` | clean |
| 52 | 0 | 0 | `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/APlusLoaderTests.java` | clean |
| 53 | 0 | 0 | `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/ComprehensivePerformanceTestTest.java` | clean |
| 54 | 0 | 5 | `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/CorpusTestResult.java` | clean |
| 55 | 0 | 0 | `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/OptimizationSwitchExhaustiveTest.java` | clean |
| 56 | 0 | 0 | `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/WutheringHeightsBufferTest.java` | clean |
| 57 | 0 | 19 | `rmatch/src/main/java/no/rmz/rmatch/compiler/ARegexpCompiler.java` | clean |
| 58 | 0 | 10 | `rmatch/src/main/java/no/rmz/rmatch/compiler/AbstractRegexBuilder.java` | clean |
| 59 | 0 | 9 | `rmatch/src/main/java/no/rmz/rmatch/compiler/CharNode.java` | clean |
| 60 | 0 | 4 | `rmatch/src/main/java/no/rmz/rmatch/compiler/CharRange.java` | clean |
| 61 | 0 | 26 | `rmatch/src/main/java/no/rmz/rmatch/compiler/CompiledFragment.java` | clean |
| 62 | 0 | 4 | `rmatch/src/main/java/no/rmz/rmatch/compiler/NDFACompilerImpl.java` | clean |
| 63 | 0 | 3 | `rmatch/src/main/java/no/rmz/rmatch/compiler/PaddingNDFANode.java` | clean |
| 64 | 0 | 112 | `rmatch/src/main/java/no/rmz/rmatch/compiler/RegexpParserException.java` | clean |
| 65 | 0 | 4 | `rmatch/src/main/java/no/rmz/rmatch/compiler/SurfaceRegexpParser.java` | clean |
| 66 | 0 | 8 | `rmatch/src/main/java/no/rmz/rmatch/compiler/TerminalNode.java` | clean |
| 67 | 0 | 69 | `rmatch/src/main/java/no/rmz/rmatch/engine/fastpath/AsciiOptimizer.java` | clean |
| 68 | 0 | 25 | `rmatch/src/main/java/no/rmz/rmatch/engine/fastpath/StateSetBuffers.java` | clean |
| 69 | 0 | 56 | `rmatch/src/main/java/no/rmz/rmatch/engine/prefilter/AhoCorasickPrefilter.java` | clean |
| 70 | 0 | 63 | `rmatch/src/main/java/no/rmz/rmatch/engine/prefilter/LiteralHint.java` | clean |
| 71 | 0 | 68 | `rmatch/src/main/java/no/rmz/rmatch/impls/CompressedDFAState.java` | clean |
| 72 | 0 | 12 | `rmatch/src/main/java/no/rmz/rmatch/impls/DominationHeap.java` | clean |
| 73 | 0 | 20 | `rmatch/src/main/java/no/rmz/rmatch/impls/MatcherFactory.java` | clean |
| 74 | 0 | 19 | `rmatch/src/main/java/no/rmz/rmatch/impls/MatcherImpl.java` | clean |
| 75 | 0 | 13 | `rmatch/src/main/java/no/rmz/rmatch/impls/NDFANodeIdMapper.java` | clean |
| 76 | 0 | 14 | `rmatch/src/main/java/no/rmz/rmatch/impls/NodeStorageImpl.java` | clean |
| 77 | 0 | 5 | `rmatch/src/main/java/no/rmz/rmatch/impls/RunnableMatchesHolderImpl.java` | clean |
| 78 | 0 | 55 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/Action.java` | clean |
| 79 | 0 | 126 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/Buffer.java` | clean |
| 80 | 0 | 71 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/DFANode.java` | clean |
| 81 | 0 | 4 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/LookaheadBuffer.java` | clean |
| 82 | 0 | 110 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/Match.java` | clean |
| 83 | 0 | 10 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/MatchEngine.java` | clean |
| 84 | 0 | 64 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/MatchSet.java` | clean |
| 85 | 0 | 65 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/Matcher.java` | clean |
| 86 | 0 | 18 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/NDFACompiler.java` | clean |
| 87 | 0 | 277 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/NDFANode.java` | clean |
| 88 | 0 | 26 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/Node.java` | clean |
| 89 | 0 | 70 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/NodeStorage.java` | clean |
| 90 | 0 | 48 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/PrintableEdge.java` | clean |
| 91 | 0 | 243 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/Regexp.java` | clean |
| 92 | 0 | 25 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/RegexpFactory.java` | clean |
| 93 | 0 | 10 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/RegexpStorage.java` | clean |
| 94 | 0 | 21 | `rmatch/src/main/java/no/rmz/rmatch/interfaces/RunnableMatchesHolder.java` | clean |
| 95 | 0 | 28 | `rmatch/src/main/java/no/rmz/rmatch/utils/CounterAction.java` | clean |
| 96 | 0 | 57 | `rmatch/src/main/java/no/rmz/rmatch/utils/CounterType.java` | clean |
| 97 | 0 | 34 | `rmatch/src/main/java/no/rmz/rmatch/utils/FastCounter.java` | clean |
| 98 | 0 | 38 | `rmatch/src/main/java/no/rmz/rmatch/utils/FastCounters.java` | clean |
| 99 | 0 | 3 | `rmatch/src/main/java/no/rmz/rmatch/utils/LifoSet.java` | clean |
| 100 | 0 | 3 | `rmatch/src/main/java/no/rmz/rmatch/utils/SimpleBloomFilter.java` | clean |
| 101 | 0 | 8 | `rmatch/src/main/java/no/rmz/rmatch/utils/SimulatedHeap.java` | clean |
| 102 | 0 | 5 | `rmatch/src/main/java/no/rmz/rmatch/utils/SortedSetComparatorImpl.java` | clean |
| 103 | 0 | 63 | `rmatch/src/main/java/no/rmz/rmatch/utils/StringBuffer.java` | clean |
| 104 | 0 | 0 | `rmatch/src/test/java/no/rmz/regepfilter/abstracts/AbstractNDFANodeTest.java` | clean |
| 105 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/bugManifestations/EdgeInvisibilityEnsurance.java` | clean |
| 106 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/compiler/SurfaceRegexpParserTest.java` | clean |
| 107 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/engine/fastpath/AsciiOptimizerTest.java` | clean |
| 108 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/engine/fastpath/StateSetBuffersTest.java` | clean |
| 109 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/engine/prefilter/AhoCorasickPrefilterPerformanceTest.java` | clean |
| 110 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/engine/prefilter/AhoCorasickPrefilterTest.java` | clean |
| 111 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/engine/prefilter/AhoPrefilterIntegrationTest.java` | clean |
| 112 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/engine/prefilter/LiteralPrefilterTest.java` | clean |
| 113 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/CompressedDFAStateTest.java` | clean |
| 114 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/CompressedIntegrationTest.java` | clean |
| 115 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/FastPathMatchEnginePrefilterThresholdTest.java` | clean |
| 116 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/LookaheadStringBufferTest.java` | clean |
| 117 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/MatchImplTest.java` | clean |
| 118 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/MatcherImplPrefilterFallbackTest.java` | clean |
| 119 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/NDFANodeIdMapperTest.java` | clean |
| 120 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/NodeStorageImplTest.java` | clean |
| 121 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/RegexpDominationProtocolTest.java` | clean |
| 122 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/RegexpImplTest.java` | clean |
| 123 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/RegexpStorageTest.java` | clean |
| 124 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/SimulatedHeapTest.java` | clean |
| 125 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/StartNodeTest.java` | clean |
| 126 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/impls/StringBufferTest.java` | clean |
| 127 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/integrationtests/APlusTest.java` | clean |
| 128 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/integrationtests/ATest.java` | clean |
| 129 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/integrationtests/AorBTest.java` | clean |
| 130 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/integrationtests/SequenceNodeTest.java` | clean |
| 131 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/optimizations/AllocationOptimizationTest.java` | clean |
| 132 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/optimizations/ComplexityImprovementDemoTest.java` | clean |
| 133 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/optimizations/FirstCharacterOptimizationTest.java` | clean |
| 134 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/optimizations/PerformanceValidationTest.java` | clean |
| 135 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/testutils/ComparableSetTest.java` | clean |
| 136 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/utils/ConcurrencyTest.java` | clean |
| 137 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/utils/CounterPerformanceTest.java` | clean |
| 138 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/utils/CounterTest.java` | clean |
| 139 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/utils/FastCounterTest.java` | clean |
| 140 | 0 | 0 | `rmatch/src/test/java/no/rmz/rmatch/utils/MultiThreadingBenchmark.java` | clean |

## Per-file Critique

### 1. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/JavaRegexpTester.java` (score 100, refs 0)
- No external references to `JavaRegexpTester` (high-confidence dead-code candidate).

### 2. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/SystemInfo.java` (score 25, refs 2)
- Very low external references to `SystemInfo` (2).
- Medium-large file (447 LOC).

### 3. `rmatch/src/main/java/no/rmz/rmatch/impls/BloomFilterMatchEngine.java` (score 25, refs 2)
- Very low external references to `BloomFilterMatchEngine` (2).
- Medium-large file (465 LOC).

### 4. `rmatch/src/main/java/no/rmz/rmatch/impls/MatchSetImpl.java` (score 22, refs 52)
- Likely-unused private methods: `progressInactiveMatch`
- Medium-large file (441 LOC).

### 5. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BenchmarkTheWutheringHeightsCorpus.java` (score 20, refs 2)
- Very low external references to `BenchmarkTheWutheringHeightsCorpus` (2).

### 6. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/MatchPairAnalysis.java` (score 20, refs 2)
- Very low external references to `MatchPairAnalysis` (2).

### 7. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/TestJavaRegexpUnfairly.java` (score 20, refs 1)
- Very low external references to `TestJavaRegexpUnfairly` (1).

### 8. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/Collector.java` (score 20, refs 1)
- Very low external references to `Collector` (1).

### 9. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/MatchDetector.java` (score 20, refs 2)
- Very low external references to `MatchDetector` (2).

### 10. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/NormalizationBenchmark.java` (score 20, refs 1)
- Very low external references to `NormalizationBenchmark` (1).

### 11. `rmatch/src/main/java/no/rmz/rmatch/compiler/AlternativesBuilder.java` (score 20, refs 2)
- Very low external references to `AlternativesBuilder` (2).

### 12. `rmatch/src/main/java/no/rmz/rmatch/compiler/AnyCharNode.java` (score 20, refs 1)
- Very low external references to `AnyCharNode` (1).

### 13. `rmatch/src/main/java/no/rmz/rmatch/compiler/CharRangeNode.java` (score 20, refs 1)
- Very low external references to `CharRangeNode` (1).

### 14. `rmatch/src/main/java/no/rmz/rmatch/compiler/CharSetBuilder.java` (score 20, refs 2)
- Very low external references to `CharSetBuilder` (2).

### 15. `rmatch/src/main/java/no/rmz/rmatch/compiler/FailNode.java` (score 20, refs 1)
- Very low external references to `FailNode` (1).

### 16. `rmatch/src/main/java/no/rmz/rmatch/compiler/StringSource.java` (score 20, refs 2)
- Very low external references to `StringSource` (2).

### 17. `rmatch/src/main/java/no/rmz/rmatch/impls/MultiMatcher.java` (score 20, refs 1)
- Very low external references to `MultiMatcher` (1).

### 18. `rmatch/src/main/java/no/rmz/rmatch/impls/RegexpStorageImpl.java` (score 20, refs 2)
- Very low external references to `RegexpStorageImpl` (2).

### 19. `rmatch/src/test/java/no/rmz/rmatch/impls/LookaheadBufferImpl.java` (score 20, refs 1)
- Very low external references to `LookaheadBufferImpl` (1).

### 20. `rmatch/src/test/java/no/rmz/rmatch/integrationtests/AlternativeCharsNode.java` (score 20, refs 1)
- Very low external references to `AlternativeCharsNode` (1).

### 21. `rmatch/src/test/java/no/rmz/rmatch/ordinaryuse/OrdinaryUsageSmokeTest.java` (score 14, refs 0)
- No direct references to `OrdinaryUsageSmokeTest`; expected for JUnit discovery.
- Likely-unused private fields: `abRegexp`, `acRegexp`

### 22. `rmatch/src/main/java/no/rmz/rmatch/impls/StartNode.java` (score 12, refs 6)
- Likely-unused private fields: `topDfaMonitor`

### 23. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/GitHubActionPerformanceTestRunner.java` (score 10, refs 0)
- No direct references to `GitHubActionPerformanceTestRunner`; likely standalone entrypoint.
- Medium-large file (471 LOC).

### 24. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BenchmarkLargeCorpus.java` (score 8, refs 0)
- No direct references to `BenchmarkLargeCorpus`; likely standalone entrypoint.
- Contains TODO/FIXME markers.

### 25. `rmatch/src/main/java/no/rmz/rmatch/impls/RegexpImpl.java` (score 8, refs 38)
- Medium-large file (308 LOC).
- Contains TODO/FIXME markers.

### 26. `rmatch/src/test/java/no/rmz/rmatch/testutils/GraphDumper.java` (score 8, refs 16)
- Medium-large file (305 LOC).
- Contains TODO/FIXME markers.

### 27. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BaselineManager.java` (score 5, refs 25)
- Medium-large file (465 LOC).

### 28. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BasicPerformanceTest.java` (score 5, refs 0)
- No direct references to `BasicPerformanceTest`; likely standalone entrypoint.

### 29. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BenchmarkTheWutheringHeightsCorpusWithMemory.java` (score 5, refs 0)
- No direct references to `BenchmarkTheWutheringHeightsCorpusWithMemory`; likely standalone entrypoint.

### 30. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/ComprehensivePerformanceTest.java` (score 5, refs 24)
- Medium-large file (342 LOC).

### 31. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/PerformanceCriteriaEvaluator.java` (score 5, refs 11)
- Medium-large file (409 LOC).

### 32. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/TestJavaRegexpUnfairlyWithMemory.java` (score 5, refs 0)
- No direct references to `TestJavaRegexpUnfairlyWithMemory`; likely standalone entrypoint.

### 33. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/MatcherBenchmarker.java` (score 5, refs 86)
- Medium-large file (331 LOC).

### 34. `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/SequenceLoaderTest.java` (score 5, refs 0)
- No direct references to `SequenceLoaderTest`; expected for JUnit discovery.
- Medium-large file (307 LOC).

### 35. `rmatch/src/main/java/no/rmz/rmatch/abstracts/AbstractNDFANode.java` (score 5, refs 36)
- Medium-large file (322 LOC).

### 36. `rmatch/src/main/java/no/rmz/rmatch/engine/prefilter/LiteralPrefilter.java` (score 5, refs 19)
- Medium-large file (319 LOC).

### 37. `rmatch/src/main/java/no/rmz/rmatch/impls/DFANodeImpl.java` (score 5, refs 32)
- Medium-large file (341 LOC).

### 38. `rmatch/src/main/java/no/rmz/rmatch/impls/FastPathMatchEngine.java` (score 5, refs 10)
- Medium-large file (388 LOC).

### 39. `rmatch/src/main/java/no/rmz/rmatch/impls/MatchEngineImpl.java` (score 5, refs 21)
- Medium-large file (358 LOC).

### 40. `rmatch/src/test/java/no/rmz/rmatch/compiler/ARegexpCompilerTest.java` (score 5, refs 0)
- No direct references to `ARegexpCompilerTest`; expected for JUnit discovery.
- Medium-large file (354 LOC).

### 41. `rmatch/src/main/java/no/rmz/rmatch/impls/MatchImpl.java` (score 3, refs 31)
- Contains TODO/FIXME markers.

### 42. `rmatch/src/test/java/no/rmz/rmatch/impls/DominationHeapTest.java` (score 3, refs 0)
- No direct references to `DominationHeapTest`; expected for JUnit discovery.
- Contains TODO/FIXME markers.

### 43. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/GitHubActionPerformanceTest.java` (score 0, refs 13)
- No major static issues detected.

### 44. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/JavaRegexpMatcher.java` (score 0, refs 7)
- No major static issues detected.

### 45. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/FileInhaler.java` (score 0, refs 12)
- No major static issues detected.

### 46. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/GraphDumper.java` (score 0, refs 16)
- No major static issues detected.

### 47. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/LineMatcher.java` (score 0, refs 5)
- No major static issues detected.

### 48. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/LineSource.java` (score 0, refs 3)
- No major static issues detected.

### 49. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/MatcherBenchmarkerWithMemory.java` (score 0, refs 4)
- No major static issues detected.

### 50. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/StringSourceBuffer.java` (score 0, refs 9)
- No major static issues detected.

### 51. `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/utils/WutheringHeightsBuffer.java` (score 0, refs 20)
- No major static issues detected.

### 52. `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/APlusLoaderTests.java` (score 0, refs 0)
- No direct references to `APlusLoaderTests`; expected for JUnit discovery.

### 53. `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/ComprehensivePerformanceTestTest.java` (score 0, refs 0)
- No direct references to `ComprehensivePerformanceTestTest`; expected for JUnit discovery.

### 54. `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/CorpusTestResult.java` (score 0, refs 5)
- No major static issues detected.

### 55. `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/OptimizationSwitchExhaustiveTest.java` (score 0, refs 0)
- No direct references to `OptimizationSwitchExhaustiveTest`; expected for JUnit discovery.

### 56. `rmatch-tester/src/test/java/no/rmz/rmatch/performancetests/WutheringHeightsBufferTest.java` (score 0, refs 0)
- No direct references to `WutheringHeightsBufferTest`; expected for JUnit discovery.

### 57. `rmatch/src/main/java/no/rmz/rmatch/compiler/ARegexpCompiler.java` (score 0, refs 19)
- No major static issues detected.

### 58. `rmatch/src/main/java/no/rmz/rmatch/compiler/AbstractRegexBuilder.java` (score 0, refs 10)
- No major static issues detected.

### 59. `rmatch/src/main/java/no/rmz/rmatch/compiler/CharNode.java` (score 0, refs 9)
- No major static issues detected.

### 60. `rmatch/src/main/java/no/rmz/rmatch/compiler/CharRange.java` (score 0, refs 4)
- No major static issues detected.

### 61. `rmatch/src/main/java/no/rmz/rmatch/compiler/CompiledFragment.java` (score 0, refs 26)
- No major static issues detected.

### 62. `rmatch/src/main/java/no/rmz/rmatch/compiler/NDFACompilerImpl.java` (score 0, refs 4)
- No major static issues detected.

### 63. `rmatch/src/main/java/no/rmz/rmatch/compiler/PaddingNDFANode.java` (score 0, refs 3)
- No major static issues detected.

### 64. `rmatch/src/main/java/no/rmz/rmatch/compiler/RegexpParserException.java` (score 0, refs 112)
- No major static issues detected.

### 65. `rmatch/src/main/java/no/rmz/rmatch/compiler/SurfaceRegexpParser.java` (score 0, refs 4)
- No major static issues detected.

### 66. `rmatch/src/main/java/no/rmz/rmatch/compiler/TerminalNode.java` (score 0, refs 8)
- No major static issues detected.

### 67. `rmatch/src/main/java/no/rmz/rmatch/engine/fastpath/AsciiOptimizer.java` (score 0, refs 69)
- No major static issues detected.

### 68. `rmatch/src/main/java/no/rmz/rmatch/engine/fastpath/StateSetBuffers.java` (score 0, refs 25)
- No major static issues detected.

### 69. `rmatch/src/main/java/no/rmz/rmatch/engine/prefilter/AhoCorasickPrefilter.java` (score 0, refs 56)
- No major static issues detected.

### 70. `rmatch/src/main/java/no/rmz/rmatch/engine/prefilter/LiteralHint.java` (score 0, refs 63)
- No major static issues detected.

### 71. `rmatch/src/main/java/no/rmz/rmatch/impls/CompressedDFAState.java` (score 0, refs 68)
- No major static issues detected.

### 72. `rmatch/src/main/java/no/rmz/rmatch/impls/DominationHeap.java` (score 0, refs 12)
- No major static issues detected.

### 73. `rmatch/src/main/java/no/rmz/rmatch/impls/MatcherFactory.java` (score 0, refs 20)
- No major static issues detected.

### 74. `rmatch/src/main/java/no/rmz/rmatch/impls/MatcherImpl.java` (score 0, refs 19)
- No major static issues detected.

### 75. `rmatch/src/main/java/no/rmz/rmatch/impls/NDFANodeIdMapper.java` (score 0, refs 13)
- No major static issues detected.

### 76. `rmatch/src/main/java/no/rmz/rmatch/impls/NodeStorageImpl.java` (score 0, refs 14)
- No major static issues detected.

### 77. `rmatch/src/main/java/no/rmz/rmatch/impls/RunnableMatchesHolderImpl.java` (score 0, refs 5)
- No major static issues detected.

### 78. `rmatch/src/main/java/no/rmz/rmatch/interfaces/Action.java` (score 0, refs 55)
- No major static issues detected.

### 79. `rmatch/src/main/java/no/rmz/rmatch/interfaces/Buffer.java` (score 0, refs 126)
- No major static issues detected.

### 80. `rmatch/src/main/java/no/rmz/rmatch/interfaces/DFANode.java` (score 0, refs 71)
- No major static issues detected.

### 81. `rmatch/src/main/java/no/rmz/rmatch/interfaces/LookaheadBuffer.java` (score 0, refs 4)
- No major static issues detected.

### 82. `rmatch/src/main/java/no/rmz/rmatch/interfaces/Match.java` (score 0, refs 110)
- No major static issues detected.

### 83. `rmatch/src/main/java/no/rmz/rmatch/interfaces/MatchEngine.java` (score 0, refs 10)
- No major static issues detected.

### 84. `rmatch/src/main/java/no/rmz/rmatch/interfaces/MatchSet.java` (score 0, refs 64)
- No major static issues detected.

### 85. `rmatch/src/main/java/no/rmz/rmatch/interfaces/Matcher.java` (score 0, refs 65)
- No major static issues detected.

### 86. `rmatch/src/main/java/no/rmz/rmatch/interfaces/NDFACompiler.java` (score 0, refs 18)
- No major static issues detected.

### 87. `rmatch/src/main/java/no/rmz/rmatch/interfaces/NDFANode.java` (score 0, refs 277)
- No major static issues detected.

### 88. `rmatch/src/main/java/no/rmz/rmatch/interfaces/Node.java` (score 0, refs 26)
- No major static issues detected.

### 89. `rmatch/src/main/java/no/rmz/rmatch/interfaces/NodeStorage.java` (score 0, refs 70)
- No major static issues detected.

### 90. `rmatch/src/main/java/no/rmz/rmatch/interfaces/PrintableEdge.java` (score 0, refs 48)
- No major static issues detected.

### 91. `rmatch/src/main/java/no/rmz/rmatch/interfaces/Regexp.java` (score 0, refs 243)
- No major static issues detected.

### 92. `rmatch/src/main/java/no/rmz/rmatch/interfaces/RegexpFactory.java` (score 0, refs 25)
- No major static issues detected.

### 93. `rmatch/src/main/java/no/rmz/rmatch/interfaces/RegexpStorage.java` (score 0, refs 10)
- No major static issues detected.

### 94. `rmatch/src/main/java/no/rmz/rmatch/interfaces/RunnableMatchesHolder.java` (score 0, refs 21)
- No major static issues detected.

### 95. `rmatch/src/main/java/no/rmz/rmatch/utils/CounterAction.java` (score 0, refs 28)
- No major static issues detected.

### 96. `rmatch/src/main/java/no/rmz/rmatch/utils/CounterType.java` (score 0, refs 57)
- No major static issues detected.

### 97. `rmatch/src/main/java/no/rmz/rmatch/utils/FastCounter.java` (score 0, refs 34)
- No major static issues detected.

### 98. `rmatch/src/main/java/no/rmz/rmatch/utils/FastCounters.java` (score 0, refs 38)
- No major static issues detected.

### 99. `rmatch/src/main/java/no/rmz/rmatch/utils/LifoSet.java` (score 0, refs 3)
- No major static issues detected.

### 100. `rmatch/src/main/java/no/rmz/rmatch/utils/SimpleBloomFilter.java` (score 0, refs 3)
- No major static issues detected.

### 101. `rmatch/src/main/java/no/rmz/rmatch/utils/SimulatedHeap.java` (score 0, refs 8)
- No major static issues detected.

### 102. `rmatch/src/main/java/no/rmz/rmatch/utils/SortedSetComparatorImpl.java` (score 0, refs 5)
- No major static issues detected.

### 103. `rmatch/src/main/java/no/rmz/rmatch/utils/StringBuffer.java` (score 0, refs 63)
- No major static issues detected.

### 104. `rmatch/src/test/java/no/rmz/regepfilter/abstracts/AbstractNDFANodeTest.java` (score 0, refs 0)
- No direct references to `AbstractNDFANodeTest`; expected for JUnit discovery.

### 105. `rmatch/src/test/java/no/rmz/rmatch/bugManifestations/EdgeInvisibilityEnsurance.java` (score 0, refs 0)
- No direct references to `EdgeInvisibilityEnsurance`; expected for JUnit discovery.

### 106. `rmatch/src/test/java/no/rmz/rmatch/compiler/SurfaceRegexpParserTest.java` (score 0, refs 0)
- No direct references to `SurfaceRegexpParserTest`; expected for JUnit discovery.

### 107. `rmatch/src/test/java/no/rmz/rmatch/engine/fastpath/AsciiOptimizerTest.java` (score 0, refs 0)
- No direct references to `AsciiOptimizerTest`; expected for JUnit discovery.

### 108. `rmatch/src/test/java/no/rmz/rmatch/engine/fastpath/StateSetBuffersTest.java` (score 0, refs 0)
- No direct references to `StateSetBuffersTest`; expected for JUnit discovery.

### 109. `rmatch/src/test/java/no/rmz/rmatch/engine/prefilter/AhoCorasickPrefilterPerformanceTest.java` (score 0, refs 0)
- No direct references to `AhoCorasickPrefilterPerformanceTest`; expected for JUnit discovery.

### 110. `rmatch/src/test/java/no/rmz/rmatch/engine/prefilter/AhoCorasickPrefilterTest.java` (score 0, refs 0)
- No direct references to `AhoCorasickPrefilterTest`; expected for JUnit discovery.

### 111. `rmatch/src/test/java/no/rmz/rmatch/engine/prefilter/AhoPrefilterIntegrationTest.java` (score 0, refs 0)
- No direct references to `AhoPrefilterIntegrationTest`; expected for JUnit discovery.

### 112. `rmatch/src/test/java/no/rmz/rmatch/engine/prefilter/LiteralPrefilterTest.java` (score 0, refs 0)
- No direct references to `LiteralPrefilterTest`; expected for JUnit discovery.

### 113. `rmatch/src/test/java/no/rmz/rmatch/impls/CompressedDFAStateTest.java` (score 0, refs 0)
- No direct references to `CompressedDFAStateTest`; expected for JUnit discovery.

### 114. `rmatch/src/test/java/no/rmz/rmatch/impls/CompressedIntegrationTest.java` (score 0, refs 0)
- No direct references to `CompressedIntegrationTest`; expected for JUnit discovery.

### 115. `rmatch/src/test/java/no/rmz/rmatch/impls/FastPathMatchEnginePrefilterThresholdTest.java` (score 0, refs 0)
- No direct references to `FastPathMatchEnginePrefilterThresholdTest`; expected for JUnit discovery.

### 116. `rmatch/src/test/java/no/rmz/rmatch/impls/LookaheadStringBufferTest.java` (score 0, refs 0)
- No direct references to `LookaheadStringBufferTest`; expected for JUnit discovery.

### 117. `rmatch/src/test/java/no/rmz/rmatch/impls/MatchImplTest.java` (score 0, refs 0)
- No direct references to `MatchImplTest`; expected for JUnit discovery.

### 118. `rmatch/src/test/java/no/rmz/rmatch/impls/MatcherImplPrefilterFallbackTest.java` (score 0, refs 0)
- No direct references to `MatcherImplPrefilterFallbackTest`; expected for JUnit discovery.

### 119. `rmatch/src/test/java/no/rmz/rmatch/impls/NDFANodeIdMapperTest.java` (score 0, refs 0)
- No direct references to `NDFANodeIdMapperTest`; expected for JUnit discovery.

### 120. `rmatch/src/test/java/no/rmz/rmatch/impls/NodeStorageImplTest.java` (score 0, refs 0)
- No direct references to `NodeStorageImplTest`; expected for JUnit discovery.

### 121. `rmatch/src/test/java/no/rmz/rmatch/impls/RegexpDominationProtocolTest.java` (score 0, refs 0)
- No direct references to `RegexpDominationProtocolTest`; expected for JUnit discovery.

### 122. `rmatch/src/test/java/no/rmz/rmatch/impls/RegexpImplTest.java` (score 0, refs 0)
- No direct references to `RegexpImplTest`; expected for JUnit discovery.

### 123. `rmatch/src/test/java/no/rmz/rmatch/impls/RegexpStorageTest.java` (score 0, refs 0)
- No direct references to `RegexpStorageTest`; expected for JUnit discovery.

### 124. `rmatch/src/test/java/no/rmz/rmatch/impls/SimulatedHeapTest.java` (score 0, refs 0)
- No direct references to `SimulatedHeapTest`; expected for JUnit discovery.

### 125. `rmatch/src/test/java/no/rmz/rmatch/impls/StartNodeTest.java` (score 0, refs 0)
- No direct references to `StartNodeTest`; expected for JUnit discovery.

### 126. `rmatch/src/test/java/no/rmz/rmatch/impls/StringBufferTest.java` (score 0, refs 0)
- No direct references to `StringBufferTest`; expected for JUnit discovery.

### 127. `rmatch/src/test/java/no/rmz/rmatch/integrationtests/APlusTest.java` (score 0, refs 0)
- No direct references to `APlusTest`; expected for JUnit discovery.

### 128. `rmatch/src/test/java/no/rmz/rmatch/integrationtests/ATest.java` (score 0, refs 0)
- No direct references to `ATest`; expected for JUnit discovery.

### 129. `rmatch/src/test/java/no/rmz/rmatch/integrationtests/AorBTest.java` (score 0, refs 0)
- No direct references to `AorBTest`; expected for JUnit discovery.

### 130. `rmatch/src/test/java/no/rmz/rmatch/integrationtests/SequenceNodeTest.java` (score 0, refs 0)
- No direct references to `SequenceNodeTest`; expected for JUnit discovery.

### 131. `rmatch/src/test/java/no/rmz/rmatch/optimizations/AllocationOptimizationTest.java` (score 0, refs 0)
- No direct references to `AllocationOptimizationTest`; expected for JUnit discovery.

### 132. `rmatch/src/test/java/no/rmz/rmatch/optimizations/ComplexityImprovementDemoTest.java` (score 0, refs 0)
- No direct references to `ComplexityImprovementDemoTest`; expected for JUnit discovery.

### 133. `rmatch/src/test/java/no/rmz/rmatch/optimizations/FirstCharacterOptimizationTest.java` (score 0, refs 0)
- No direct references to `FirstCharacterOptimizationTest`; expected for JUnit discovery.

### 134. `rmatch/src/test/java/no/rmz/rmatch/optimizations/PerformanceValidationTest.java` (score 0, refs 0)
- No direct references to `PerformanceValidationTest`; expected for JUnit discovery.

### 135. `rmatch/src/test/java/no/rmz/rmatch/testutils/ComparableSetTest.java` (score 0, refs 0)
- No direct references to `ComparableSetTest`; expected for JUnit discovery.

### 136. `rmatch/src/test/java/no/rmz/rmatch/utils/ConcurrencyTest.java` (score 0, refs 0)
- No direct references to `ConcurrencyTest`; expected for JUnit discovery.

### 137. `rmatch/src/test/java/no/rmz/rmatch/utils/CounterPerformanceTest.java` (score 0, refs 0)
- No direct references to `CounterPerformanceTest`; expected for JUnit discovery.

### 138. `rmatch/src/test/java/no/rmz/rmatch/utils/CounterTest.java` (score 0, refs 0)
- No direct references to `CounterTest`; expected for JUnit discovery.

### 139. `rmatch/src/test/java/no/rmz/rmatch/utils/FastCounterTest.java` (score 0, refs 0)
- No direct references to `FastCounterTest`; expected for JUnit discovery.

### 140. `rmatch/src/test/java/no/rmz/rmatch/utils/MultiThreadingBenchmark.java` (score 0, refs 0)
- No direct references to `MultiThreadingBenchmark`; expected for JUnit discovery.

