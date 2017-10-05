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

package no.rmz.regepfilter.abstracts;

import java.util.Collection;
import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;


/**
 * Test basic functions  in the AbstractNDFANode class.
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractNDFANodeTest {



    /**
     * The test article.
     */
    private AbstractNDFANode testArticleNDFANode;

    /**
     * Mocked regexp, treated as an opaque instance.
     */
    @Mock
    Regexp regexp;
    /**
     * We need an NDFANode that is treated as an opaque object.
     */
    @Mock
            NDFANode ndfanode;


    /**
     * Make a new test article.
     */
    @Before
    public void setUp() {
        /**
         * Make a new, terminal node (that's what the "true"
         * indicates).
         */
        testArticleNDFANode = new AbstractNDFANodeImpl(regexp, true);
    }


    /**
     * The constructor should make a terminal node, so we
     * check that it is.
     */
    @Test
    public  final void testIsTerminal() {
        assertTrue(testArticleNDFANode.isTerminal());
    }

    /**
     * Get the stored regexp and check that it's the one we put
     * into it.
     */
    @Test
    public  final void testGetRegexp() {
        assertEquals(regexp, testArticleNDFANode.getRegexp());
    }

    /**
     * Check that when checking if the test
     * article is active, that check involves asking
     * the regexp if it's active for the test article.
     */
    @Test
    public final  void testIsActiveForWhenFalse() {
        assertTrue(!testArticleNDFANode.isActiveFor(regexp));
        verify(regexp).isActiveFor(testArticleNDFANode);
    }

    /**
     * Check that when setting that a regexp is active for a node,
     * then the node will be seen as active.
     */
    @Test
    public  final void testIsActiveForWhenTrue() {
        when(regexp.isActiveFor(testArticleNDFANode)).thenReturn(true);

        assertTrue(testArticleNDFANode.isActiveFor(regexp));
        verify(regexp).isActiveFor(testArticleNDFANode);
    }

    /**
     * Check that the test article is terminal for the regexp.
     */
    @Test
    public  final void testIsFinalFor() {
        assertTrue(!testArticleNDFANode.isTerminalFor(regexp));
    }

    /**
     * Unimplemented test.
     */
    @Ignore @Test
    public final  void testGetNextSet() {
    }

    /**
     * Check that the default (zero) number of epsilons is really
     * what we're starting with.
     */
    @Test
    public  final void testGetEpsilons() {
        // By default this true
        assertTrue(testArticleNDFANode.getEpsilons().isEmpty());
    }


    /**
     * Test adding an epsilon edge, and checking that it was
     * indeed added.
     */
    @Test
    public  final void testAddEpsilonReachableNode() {
        testArticleNDFANode.addEpsilonEdge(ndfanode);

        assertTrue(!testArticleNDFANode.getEpsilons().isEmpty());
        assertTrue(testArticleNDFANode.getEpsilons().contains(ndfanode));
    }

    /**
     * Remove an epsilon edge and check that it is indeed removed.
     */
    @Test
    public final void testRemoveEpsilonReachableNode() {
        testArticleNDFANode.addEpsilonEdge(ndfanode);
        testArticleNDFANode.removeEpsilonReachableNode(ndfanode);
        assertTrue(testArticleNDFANode.getEpsilons().isEmpty());
        assertTrue(!testArticleNDFANode.getEpsilons().contains(ndfanode));
    }
    /**
     * A really simple implementation of an abstract node.  We need to
     * make an implementation. Since the AbstractNDFANode is, well,
     * abstract, and can't be instantiated (yeah yeah, anonymous classes
     * etc.)
     */
    public static final class AbstractNDFANodeImpl extends AbstractNDFANode {
        
        /**
         * Create a new instance of the test article.
         * @param r a regexp.
         * @param isTerminal True iff this node is terminal.
         */
        public AbstractNDFANodeImpl(
                final Regexp r,
                final boolean isTerminal) {
            super(r, isTerminal);
        }
        
        @Override
        public NDFANode getNextNDFA(final Character ch) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
        @Override
        public Collection<PrintableEdge> getEdgesToPrint() {
            return getEpsilonEdgesToPrint();
        }
    }
}
