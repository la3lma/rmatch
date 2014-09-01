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

import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.RegexpStorage;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.NodeStorage;

import java.util.*;

import no.rmz.rmatch.compiler.RegexpParserException;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

import org.mockito.runners.MockitoJUnitRunner;

/**
 * Test the RegexpStorage.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RegexpStorageTest {

    /**
     * The test article.
     */
    private RegexpStorage rs;

    /**
     * A string holding a regular expression.
     */
    private String reString;

    /**
     * A set of strings that is used when checking that the
     * strings representing regexps in the RegexpStorage
     * contains a specific set of regexps strings, i.e. the set wee
     * have put into the storage.
     */
    private Set<String> stringSet;

    /**
     * A mocked compiler.  The behavior is mocked up to return
     * the compilationResult node as the result of any compilation.
     */
    @Mock
    NDFACompiler compiler;

    /**
     * Placeholder mock. Not used for anything as far as the tests
     * are concerned (but necessary to obey contracts with interfaces).
     */
    @Mock
    NodeStorage storage;


    /**
     * The compilation result. Just mocked, treated as an opaque object.
     */
    @Mock
    NDFANode compilationResult;


    /**
     * A mocked action, only treated as an opaque object.
     */
    @Mock
    Action a;

    /**
     * Set up, will poulate the variables reString, stringSet and rs, as
     * well as mocking up the behavior of the compile instance to return
     * the content compilationResult whenever the compile method is
     * invoked.
     */
    @Before
    public void setUp() throws RegexpParserException {
        reString = "Fnord";
        stringSet = new HashSet<String>();
        rs = new RegexpStorageImpl(storage, compiler);

        // A really simple compiler, always returns the same
        // (mocked) node.
        when(compiler.compile((Regexp) any(),
                (RegexpStorage) any())).thenReturn(compilationResult);
    }

    /**
     * Adding and removing regexps.
     * @throws no.rmz.rmatch.compiler.RegexpParserException
     */
    @Test
    public void testAddRemoveRegexp() throws RegexpParserException {
        assertTrue(!rs.hasRegexp(reString));
        rs.add(reString, a);
        assertTrue(rs.hasRegexp(reString));
        rs.remove(reString, a);
        assertTrue(!rs.hasRegexp(reString));
    }

    /**
     * Retrieving stuff that is stored.
     */
    @Test
    public void testGetRegexp() {
        final Regexp re = rs.getRegexp(reString);
        assertTrue(rs.hasRegexp(reString));
        assertTrue(rs.getRegexp(reString) == re);
    }

    /**
     * Checking that all the regexps that are put into
     * the storage is actually there.
     */
    @Test
    public void testRegexpSetInclusion() {
        rs.getRegexp(reString);
        stringSet.add(reString);
        assertTrue(rs.getRegexpSet().containsAll(stringSet));
    }

    /**
     * Don't know what this tests. XXX Figure out what this does.
     */
    @Test
    public void testRegexpSetExclusion() {
        rs.getRegexp(reString);
        final Set s1 = rs.getRegexpSet();
        stringSet.add(reString);
        s1.removeAll(stringSet);
        assertTrue(s1.isEmpty());
    }
}
