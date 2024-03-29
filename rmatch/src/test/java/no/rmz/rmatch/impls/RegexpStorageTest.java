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

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * Test the RegexpStorage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    @BeforeEach
    public void setUp() throws RegexpParserException {
        reString = "Fnord";
        stringSet = new HashSet<>();
        rs = new RegexpStorageImpl(storage, compiler);

        // A really simple compiler, always returns the same
        // (mocked) node.
        when(compiler.compile(any(),
                any())).thenReturn(compilationResult);
    }

    /**
     * Adding and removing regexps.
     */
    @Test
    public void testAddRemoveRegexp() throws RegexpParserException {
        assertFalse(rs.hasRegexp(reString));
        rs.add(reString, a);
        assertTrue(rs.hasRegexp(reString));
        rs.remove(reString, a);
        assertFalse(rs.hasRegexp(reString));
    }

    /**
     * Retrieving stuff that is stored.
     */
    @Test
    public void testGetRegexp() {
        final Regexp re = rs.getRegexp(reString);
        assertTrue(rs.hasRegexp(reString));
        assertSame(rs.getRegexp(reString), re);
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
        final Set<String> s1 = rs.getRegexpSet();
        stringSet.add(reString);
        s1.removeAll(stringSet);
        assertTrue(s1.isEmpty());
    }
}
