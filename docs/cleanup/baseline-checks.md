# Cleanup Baseline Checks

Timestamp (UTC): 2026-03-04T16:22:39Z

## Commands

### mvn -q -B -DskipTests -Dspotbugs.skip=true package
- exit_code: 0
- duration_seconds: 6
- tail_output:
```
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::staticFieldBase has been called by com.google.inject.internal.aop.HiddenClassDefiner (file:/usr/local/Cellar/maven/3.9.11/libexec/lib/guice-5.1.0-classes.jar)
WARNING: Please consider reporting this to the maintainers of class com.google.inject.internal.aop.HiddenClassDefiner
WARNING: sun.misc.Unsafe::staticFieldBase will be removed in a future release
```

### mvn -q -B -pl rmatch,rmatch-tester test
- exit_code: 1
- duration_seconds: 96
- tail_output:
```
engine=default,prefilter=none,threshold=5000 => 20425 ms, 2129 MB
engine=default,prefilter=aho,threshold=99999 => 105 ms, 2966 MB
engine=default,prefilter=aho,threshold=5000 => 53 ms, 3806 MB
engine=bloom,prefilter=none,threshold=99999 => 991 ms, 2182 MB
engine=bloom,prefilter=none,threshold=5000 => 256 ms, 3959 MB
engine=bloom,prefilter=aho,threshold=99999 => 229 ms, 2279 MB
engine=bloom,prefilter=aho,threshold=5000 => 205 ms, 4038 MB
engine=fastpath,prefilter=none,threshold=99999 => 20532 ms, 2386 MB
engine=fastpath,prefilter=none,threshold=5000 => 20352 ms, 4287 MB
engine=fastpath,prefilter=aho,threshold=99999 => 68 ms, 1210 MB
engine=fastpath,prefilter=aho,threshold=5000 => 58 ms, 2046 MB
[ERROR] Failures: 
[ERROR]   SequenceLoaderTest.testWutheringHeightsCorpus:149 expected: <true> but was: <false>
[ERROR]   SequenceLoaderTest.testWutheringHeightsCorpusWithSomeRegexps:209->testWithRegexpsFromFile:296 Not enough matches, got only 0 but expected at least 206. ==> expected: <true> but was: <false>
[ERROR]   SequenceLoaderTest.testWutheringHeightsCorpusWithVeryFewRegexps:173->testWithRegexpsFromFile:296 Not enough matches, got only 0 but expected at least 42. ==> expected: <true> but was: <false>
[ERROR] Tests run: 16, Failures: 3, Errors: 0, Skipped: 2
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.4:test (default-test) on project rmatch-tester: There are test failures.
[ERROR] 
[ERROR] See /Volumes/SynologyScsi1/git/rmatch/rmatch-tester/target/surefire-reports for the individual test results.
[ERROR] See dump files (if any exist) [date].dump, [date]-jvmRun[N].dump and [date].dumpstream.
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
[ERROR] 
[ERROR] After correcting the problems, you can resume the build with the command
[ERROR]   mvn <args> -rf :rmatch-tester
```

### (cd benchmarking/framework/regex_bench_framework && make test-unit)
- exit_code: 2
- duration_seconds: 0
- tail_output:
```
\033[0;34mRunning Python unit tests...\033[0m 
.venv/bin/pytest tests/ -v
make: .venv/bin/pytest: No such file or directory
make: *** [test-unit] Error 1
```

### (cd benchmarking/framework/regex_bench_framework && make test-quick)
- exit_code: 2
- duration_seconds: 11
- tail_output:
```
[checking no.rmz.rmatch.mockedcompiler.CharPlusNode]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/mockedcompiler/CharPlusNode.class]
[checking no.rmz.rmatch.mockedcompiler.CharSequenceCompiler]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/mockedcompiler/CharSequenceCompiler.class]
[checking no.rmz.rmatch.mockedcompiler.MockerCompiler]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/mockedcompiler/MockerCompiler.class]
[checking no.rmz.rmatch.utils.Counter]
[loading /modules/java.base/java/lang/IllegalStateException.class]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/Counter.class]
[checking no.rmz.rmatch.utils.CounterAction]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/CounterAction.class]
[checking no.rmz.rmatch.utils.Counters]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/Counters.class]
[checking no.rmz.rmatch.utils.FastCounters]
[loading /modules/java.base/java/lang/reflect/Field.class]
[loading /modules/java.base/java/lang/NoSuchFieldException.class]
[loading /modules/java.base/java/lang/reflect/Member.class]
[loading /modules/java.base/java/lang/reflect/AccessibleObject.class]
[loading /modules/java.base/java/lang/ReflectiveOperationException.class]
[loading /modules/java.base/java/lang/IllegalAccessException.class]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/FastCounters.class]
[checking no.rmz.rmatch.utils.LifoSet]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/LifoSet.class]
[checking no.rmz.rmatch.utils.ObjectPool]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/ObjectPool.class]
[checking no.rmz.rmatch.utils.SimulatedHeap]
[loading /modules/java.base/java/util/SequencedMap.class]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/SimulatedHeap.class]
[checking no.rmz.rmatch.utils.SimulatedHeapConcurrent]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/SimulatedHeapConcurrent.class]
[checking no.rmz.rmatch.utils.SortedSetComparatorImpl]
[loading /modules/java.base/java/util/function/ToDoubleFunction.class]
[loading /modules/java.base/java/util/function/ToLongFunction.class]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/SortedSetComparatorImpl.class]
[checking no.rmz.rmatch.utils.StringBuffer]
[wrote /Volumes/SynologyScsi1/git/rmatch/rmatch/target/classes/no/rmz/rmatch/utils/StringBuffer.class]
[total 1056ms]
[ERROR] High: Dead store to allRegexps in no.rmz.rmatch.impls.BloomFilterMatchEngine.initialize(Set) [no.rmz.rmatch.impls.BloomFilterMatchEngine] At BloomFilterMatchEngine.java:[line 74] DLS_DEAD_LOCAL_STORE
✓ rmatch installed to local .m2 repository
✓ Found rmatch JAR: /Users/rmz/.m2/repository/no/rmz/rmatch/1.1-SNAPSHOT/rmatch-1.1-SNAPSHOT.jar
Copying rmatch dependencies from /Volumes/SynologyScsi1/git/rmatch/rmatch/target/lib...
✓ Copied       20 dependency JARs
Compiling rmatch benchmark...
/Volumes/SynologyScsi1/git/rmatch/benchmarking/framework/regex_bench_framework/engines/rmatch/RMatchBenchmark.java:87: warning: [removal] runFinalization() in Runtime has been deprecated and marked for removal
            runtime.runFinalization();
                   ^
1 warning
✓ rmatch benchmark compiled successfully
✓ Build complete!
  Executable: /Volumes/SynologyScsi1/git/rmatch/benchmarking/framework/regex_bench_framework/engines/rmatch/.build/run_rmatch_benchmark.sh
  rmatch JAR: /Users/rmz/.m2/repository/no/rmz/rmatch/1.1-SNAPSHOT/rmatch-1.1-SNAPSHOT.jar

Test with:
  /Volumes/SynologyScsi1/git/rmatch/benchmarking/framework/regex_bench_framework/engines/rmatch/.build/run_rmatch_benchmark.sh patterns.txt corpus.txt
\033[0;34mRunning quick validation tests...\033[0m 
.venv/bin/regex-bench -v run-phase \
		--config test_matrix/quick_validation.json \
		--output results/quick_20260304_172500
make: .venv/bin/regex-bench: No such file or directory
make: *** [test-quick] Error 1
```

---

## Baseline Refresh After Prefilter Fix

Timestamp (UTC): 2026-03-04T21:40:00Z

### Commands

#### mvn -q -B -DskipTests -Dspotbugs.skip=true package
- exit_code: 0
- summary:
  - package/build succeeded
  - warning spam only (Unsafe + deprecations)

#### mvn -q -B -pl rmatch,rmatch-tester test
- exit_code: 0
- summary:
  - core unit/integration tests passed
  - `SequenceLoaderTest` now passes with expected non-zero matches (`very-few`: 533, `some`: 2080)

#### (cd benchmarking/framework/regex_bench_framework && make test-unit)
- exit_code: 2
- summary:
  - fails because framework has no `tests/` directory
  - make target currently executes `.venv/bin/pytest tests/ -v`
  - observed pytest message: `ERROR: file or directory not found: tests/`

#### (cd benchmarking/framework/regex_bench_framework && make test-quick)
- exit_code: 2
- summary:
  - engine build succeeds, but quick run target fails because `.venv/bin/regex-bench` is not present after `venv` bootstrap alone
  - observed make tail:
    - `.venv/bin/regex-bench -v run-phase ...`
    - `No such file or directory`

### Notes

1. Core Java baseline gate is now green and suitable for cleanup-step regression checks.
2. Framework Make targets are currently inconsistent:
   - `test-unit` assumes a `tests/` tree that is absent.
   - `test-quick` assumes package install (`regex-bench`) even though it only depends on `venv` and `build-engines`.
