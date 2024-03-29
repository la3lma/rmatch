/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.impls;

import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.utils.StringBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test the NodeStorage implementation for a few simple usecases.
 */
@ExtendWith(MockitoExtension.class)
class NodeStorageImplTest {

    /**
     * An implementation instance. This is the test article that we check the
     * response of.
     */
    private NodeStorageImpl nsi;
    /**
     * A mocked NDFANode that will be used in various ways. The tests will set
     * up this variable for themselves.
     */
    @Mock
    NDFANode ndfaNode;
    /**
     * A buffer that is used to simulate input.
     */
    private Buffer buffer;
    /**
     * A set of NDFANodes that is never added to.
     */
    private SortedSet<NDFANode> emptyNdfaNodeSet;

    /**
     * Check any invariants that should be true for the globally declared data
     * structures are actually true post ante.
     */
    @AfterEach
    public final void tearDown() {
        assertTrue(emptyNdfaNodeSet.isEmpty(),
                "Expecting emptyNdfaNodeSet to be empty");
    }

    /**
     * Set up a new NodeStorageImpl, a StringBuffer containing the string "a",
     * and an empty tree set in the emptyTreeSet field.
     */
    @BeforeEach
    public final void setUp() {
        nsi = new NodeStorageImpl();
        buffer = new StringBuffer("a");
        emptyNdfaNodeSet = new TreeSet<>();
    }

    /**
     * Test adding to the startnode.
     */
    @Test
    public final void testAddToStartnode() {
        assertFalse(nsi.isConnectedToStartnode(ndfaNode), "Node shouldn't initially be stored");
        nsi.addToStartnode(ndfaNode);
        assertTrue(nsi.isConnectedToStartnode(ndfaNode),
                "Node should now be stored");
    }

    /**
     * XXX Incomplete test of getting a next character from the buffer.
     */
    @Test
    public final void testGetNext() {
        // XXX Incomplete test
        nsi.getNextFromStartNode(buffer.getNext());
    }

    /**
     * A corner case: to test is where we have an empty set of NDFANOdes, do we
     * get the same DFA when we look for it twice (with different empty set
     * instances)?
     */
    @Test
    public final void testGetDFANodeFromEmptySet() {
        final DFANode dfaAmpty = nsi.getDFANode(emptyNdfaNodeSet);
        assertEquals(dfaAmpty, nsi.getDFANode(new TreeSet<>()));
    }
    /**
     * Mock a regexp. We won't ever look into this regexp it's just treated as
     * an opaque object as far as the tests are concerned.
     */
    @Mock
    Regexp regexp;

    /**
     * If we have a node based on a single NDFA, do we get the same DFA node
     * when looking for it twice?
     */
    @Test
    public final void testGetDFANodeFromSingeltonSet() {


        final SortedSet<NDFANode> singeltonSet = new TreeSet<>();

        when(ndfaNode.getRegexp()).thenReturn(regexp);
        singeltonSet.add(ndfaNode);

        final DFANode dfaSingelton = nsi.getDFANode(singeltonSet);

        assertTrue(dfaSingelton.getRegexps().contains(regexp));
        assertEquals(dfaSingelton, nsi.getDFANode(singeltonSet));
    }
    /**
     * Mock a regexp. We won't ever look into this regexp it's just treated as
     * an opaque object as far as the tests are concerned.
     */
    @Mock
    Regexp r1;

    /**
     * Mock a regexp. We won't ever look into this regexp it's just treated as
     * an opaque object as far as the tests are concerned.
     */
    @Mock
    Regexp r2;

    /**
     * Mock a regexp. We won't ever look into this regexp it's just treated as
     * an opaque object as far as the tests are concerned.
     */
    @Mock
    Regexp r3;

    /**
     * A class that will accept all sequences of all characters.
     * This is essntially an implementation of the regular
     * expression ".+".
     */
    public static final class LoopyNdfaNode extends AbstractNDFANode {

        /**
         * A new loopy node.
         * @param r the regexp that node represents.
         */
        public LoopyNdfaNode(final Regexp r) {
            super(r, true);
        }

        @Override
        public NDFANode getNextNDFA(final Character ch) {
            return this;
        }

        @Override
        public Collection<PrintableEdge> getEdgesToPrint() {
            return getEpsilonEdgesToPrint();
        }
    }

    /**
     * We now set up three NDFANodes based the three size two subsets of a set
     * of size three and make sure that we get the right DFANOdes for many kinds
     * of lookups using these combinations.
     */
    @Test
    public final void testTwoOutOfThreeSubsetLooups() {

        // Mocking up the nodes.
        final NDFANode n1 = new LoopyNdfaNode(r1);
        final NDFANode n2 = new LoopyNdfaNode(r2);
        final NDFANode n3 = new LoopyNdfaNode(r3);

        // Setting up the fixture for this test.
        final SortedSet<NDFANode> s12d1 = new TreeSet<>();
        final SortedSet<NDFANode> s12d2 = new TreeSet<>();

        s12d1.add(n1);
        s12d1.add(n2);

        s12d2.add(n1);
        s12d2.add(n2);

        final SortedSet<NDFANode> s13d1 = new TreeSet<>();
        final SortedSet<NDFANode> s13d2 = new TreeSet<>();

        s13d1.add(n1);
        s13d1.add(n3);

        s13d2.add(n1);
        s13d2.add(n3);

        final SortedSet<NDFANode> s23d1 = new TreeSet<>();
        final SortedSet<NDFANode> s23d2 = new TreeSet<>();

        s23d1.add(n2);
        s23d1.add(n3);

        s23d2.add(n2);
        s23d2.add(n3);

        // A bunch of equalities that should all hold
        assertEquals(nsi.getDFANode(s23d2), nsi.getDFANode(s23d1));
        assertEquals(nsi.getDFANode(s13d2), nsi.getDFANode(s13d1));
        assertEquals(nsi.getDFANode(s12d2), nsi.getDFANode(s12d1));

        // And a bunch of inequalities that should all hold
        final DFANode s23dfa = nsi.getDFANode(s23d2);
        final DFANode s13dfa = nsi.getDFANode(s13d1);

        assertNotSame(s23dfa, s13dfa);
        assertNotSame(nsi.getDFANode(s23d2), nsi.getDFANode(s13d2));
        assertNotSame(nsi.getDFANode(s23d2), nsi.getDFANode(s12d1));
        assertNotSame(nsi.getDFANode(s23d2), nsi.getDFANode(s12d2));

        // Could have been many more... but not today.
    }
}
