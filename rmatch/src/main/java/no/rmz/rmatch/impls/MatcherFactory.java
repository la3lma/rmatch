/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package no.rmz.rmatch.impls;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.RegexpFactory;


/**
 * A factory instance that will generate matcher instances that
 * are optimized for the  urrent execution environment.  It's
 * all heuristic, but it it ment to represent the best guess, based
 * on the available empirical data as to what willl give the
 * best performance.
 */
public class MatcherFactory {

    /**
     * Overbooking factor, to try facilitate better utilization
     * of available processors.
     */

    private final static int OVERBOOKING = 5;

    /**
     * A management bean that we use to probe the execution environment.
     */
    private static final OperatingSystemMXBean OS_MBEAN
            = ManagementFactory.getOperatingSystemMXBean();

    /**
     * The number of processors available to us.
     */
    private static final int no_of_available_processors
            = OS_MBEAN.getAvailableProcessors();



    /**
     * Return a matcher that is assumed to be optimal for the current execution
     * environment. This is the recommenced way to get a matcher to use.
     * @return a new Matcher instance.
     */
    public final static Matcher newMatcher() {

        final int noOfPartitions = no_of_available_processors * OVERBOOKING;


        return new MultiMatcher(
                noOfPartitions,
                new NDFACompilerImpl(),
                RegexpFactory.DEFAULT_REGEXP_FACTORY);
    }
}
