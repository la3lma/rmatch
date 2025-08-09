package no.rmz.rmatch.benchmarks;

import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.StringBuffer;
import org.openjdk.jmh.annotations.*;


import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class CompileAndMatchBench {

    @Param({"10","100","1000"})
    public int patternCount;

    private List<String> patterns;
    private String haystack;

    @Setup
    public void setup() {
        patterns = IntStream.range(0, patternCount).mapToObj(i -> "a.*b" + i).toList();
        haystack = "aaa bbb ccc a---b999 end";
    }

    @Benchmark
    public Matcher buildMatcher() {
        Matcher m = MatcherFactory.newMatcher();
        for (String p : patterns) {
            // TODO: replace with your actual API for adding patterns
            // m.addPattern(p.getBytes(StandardCharsets.UTF_8));


        }
        return m;
    }

    @Benchmark
    public  void matchOnce() {
        Matcher m = MatcherFactory.newMatcher();
        for (String p : patterns) {
            // m.addPattern(p.getBytes(StandardCharsets.UTF_8));
        }
        // TODO: replace with your actual match method returning a count or status
        haystack = "banana";
        Buffer buf = new StringBuffer(haystack);
        m.match(buf);
    }
}
