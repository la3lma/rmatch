# Right now

*  Delete the funkiness introduced for lookahead buffers.  Turns out it is wasn't necessary.
* Look at how to make static string regexps even more efficient at pruning based on first char.
* Consider making a notebook to track progress over time instead of the wretched shellscripts currently there.
* At the very least, fix those scripts.
* Introduce more tests that stresses the scale more.  The "unfair comparison" may be a little too unfair.
* Refactor a bunch of code to be more java-20 compliant.  Do use chat-gpt for that.
* Find some better way to interpret benchmarking results.
* Would it make sense to write a piece of code that uses chat-gpt to generate attempts at refactoring after running them through unit tests?

# Some time in the future

* Do everything in http://maven.apache.org/guides/mini/guide-central-repository-upload.html
  that is necessary to get released through maven central, then decide that it isn't worth it
  and instead set up a microrepo at github, at least for now.
* Or perhaps use sonatype's stuff. https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide#SonatypeOSSMavenRepositoryUsageGuide-1a.TermsofServiceRepository%3ACentralRepositoryTermsofServiceforSubmitters
* Tag a release.
* Write a minimal set of documentation for that release.
