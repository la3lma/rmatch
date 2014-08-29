/*
 * Copyright 2013 rmz.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rmz.rmatch.utils;

import static java.util.concurrent.Executors.newFixedThreadPool;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 *
 * @author rmz
 */
public class CounterTest {

    public CounterTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of inc method, of class Counter.
     */
    @Test
    public void testMultiThreadedCounterAccess() {
        final Counter counter = new Counter("Sample counter");
        final int noOfThreadsInPool = 10;
        final int noOfIterationsInRunnable = 10000;
        final int noOfRunnables = noOfThreadsInPool;
        final int noOfIncrements = noOfRunnables * noOfIterationsInRunnable;
        final ExecutorService executors =
                newFixedThreadPool(noOfThreadsInPool);

        final Collection<Callable<Object>> runnables = new ArrayList<Callable<Object>>();

        for (int j = 0; j < noOfRunnables; j++) {
            final Callable runnable =
                    new Callable() {
                @Override
                public Object call() throws Exception {
                    for (int i = 0; i < noOfIterationsInRunnable; i++) {
                        counter.inc();
                    }
                    return null;
                }
            };
            runnables.add(runnable);
        }

        try {
            executors.invokeAll(runnables);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        assertEquals(noOfIncrements, counter.inc() - 1);
    }
}