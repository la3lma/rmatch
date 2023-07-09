# Right now
* Rethink where we can make huge cutoffs, and maybe get some more speed.
   * Make some statistics. What I'm mostly interested in, is non-matching
     matches.  Matches that are terminated without finding what they are looking for.
     If this can be measured (and it can), then it can be used to determine
     where to make cutoffs.  If for instance, the most common cutoff is
     for length 1, then we can optimize for a length 1 cutoff using a
     lookup schem that is optimized for length 1.  If the most common case.
* Consider using assert instead of notnull, to ensure runtime efficiency.
* Set up a GitHub action build for PRs (and main).
* Set up code quality feedback for the builds.
* If it makes sense, put some of the main statistics on the front page to announce where this is headed.
* Take a look at https://stackoverflow.com/questions/7989658/java-what-is-the-overhead-of-using-concurrentskiplist-when-no-concurrency-is-ne
  and see if it is worth it to use ConcurrentSkipListMap instead of TreeMap, or even a sorted list with no concurrency.
  The heuristic seems to be that the added overhead for the concurrently safe things are only worth wile doing
  if there is a lot of concurrent processing or a lot of data,
  the way we have partitioned the things we are working on that may not be the case.
  Perhaps some benchmarking on the size of the set would be in order.
* Wrap it up
* Make sure it is correct
* Start looking for more ways to fix concurrency issues.
* Using the focused test, figure out what goes wrong.
   * Print out the NDFA graph
   * Maybe also the DFA graph, but that probably is less useful.
   * Scratch head until enlightenment hits.
  https://docs.github.com/en/actions/automating-builds-and-tests/building-and-testing-java-with-maven
* Look at how to make static string regexps even more efficient at pruning based on first char.
* Consider making a notebook to track progress over time instead of the wretched shell scripts currently there.
* At the very least, fix those scripts.
* Introduce more tests that stress the scale more.  The "unfair comparison" may be a little too unfair.
* Refactor a bunch of code to be more java-20 compliant.  Do use chat-gpt for that.
* Find some better way to interpret benchmarking results.
* Would it make sense to write a piece of code that uses chat-gpt to generate attempts at refactoring after running them through unit tests?

# Some time in the future

* Do everything in http://maven.apache.org/guides/mini/guide-central-repository-upload.html
  that is necessary to get released through maven central, then decide that it isn't worth it
  and instead set up a micro-repo at GitHub, at least for now.
* Or perhaps use sonatype's stuff. https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide#SonatypeOSSMavenRepositoryUsageGuide-1a.TermsofServiceRepository%3ACentralRepositoryTermsofServiceforSubmitters
* Tag a release.
* Write a minimal set of documentation for that release.

