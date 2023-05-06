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

import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.NodeStorage;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test the start node.
 */
@ExtendWith(MockitoExtension.class)
public class StartNodeTest {

    /**
     * A mocked NodeStorage.  It's not actually
     * used for anything by these tests.
     */
    @Mock
    public NodeStorage ns;

    /**
     * A mocked NDFANode.  Only used to have something
     * to add to the startnode.  No state change in the
     * node expected or checked for.
     */
    @Mock
    public NDFANode ndfaNode;


    /**
     * Test that adding a node results in a node being added. ;-)
     */
    @Test
    public final void testAddingNode() {

        final StartNode startNode = new StartNode(ns);
        assertTrue("Node shouldn't initially be stored",
                !startNode.getEpsilons().contains(ndfaNode));
        startNode.add(ndfaNode);
        assertTrue("Node shouldn now be stored",
                startNode.getEpsilons().contains(ndfaNode));
    }
}
