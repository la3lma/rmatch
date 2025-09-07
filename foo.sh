 # Run the performance test with our new GitHubActionPerformanceTest
MAX_REGEXPS=1000  # Limit for CI performance
NUM_RUNS=3       # Minimum for statistical significance
          
./mvnw -q -B -pl rmatch-tester exec:java \
   -Dexec.mainClass=no.rmz.rmatch.performancetests.GitHubActionPerformanceTestRunner \
   -Dexec.args="${MAX_REGEXPS} ${NUM_RUNS}" \
   || exit 1
      
