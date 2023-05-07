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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 *
 * @author rmz
 */
public class CounterTest {

    public CounterTest() {
    }

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
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
                Executors.newFixedThreadPool(noOfThreadsInPool);

        final Collection<Callable<Object>> runnables = new ArrayList<>();

        for (int j = 0; j < noOfRunnables; j++) {
            final Callable<Object> runnable = () -> {
                        for (int i = 0; i < noOfIterationsInRunnable; i++) {
                            counter.inc();
                        }
                        return null;
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