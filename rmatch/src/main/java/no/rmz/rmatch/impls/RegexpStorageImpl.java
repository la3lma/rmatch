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

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An implementation of the regular expression storage interface. It stores all
 * of its regular expressions in memory.
 */
public final class RegexpStorageImpl implements RegexpStorage {

    /**
     * A map mapping string representations of regular expressions to Regexps
     * instances representing those strings.
     *
     */
    private final Map<String, Regexp> regexps = new HashMap<>();
    /**
     * A NodeStorage instance that is used when creating new nondeterminstic
     * nodes.
     */
    private final NodeStorage storage;
    /**
     * A compiler that is used when compiling strings into nondeterminstic
     * nodes.
     */
    private final NDFACompiler compiler;
    /**
     * A factory that will produce a Regexp instance for our strings.
     */
    private final RegexpFactory regexpFactory;

    /**
     * Create a new RegexpStorageImpl instance using the default regexp factory.
     * This is the creator that should be used in production settings.
     *
     * @param storage the NodeStorage.
     * @param compiler the compiler.
     */
    public RegexpStorageImpl(
            final NDFACompiler compiler) {
        this(compiler, RegexpFactory.DEFAULT_REGEXP_FACTORY);
    }

    /**
     * This creator allows a particular RegexpFactory to be used. This creator
     * is only intended to be used to inject mocked RegexpFactories during
     * testing.
     *
     * @param storage the NodeStorage.
     * @param compiler the compiler.
     * @param regexpFactory A RegexpFactory instance to use.
     */
    public RegexpStorageImpl(
            final NDFACompiler compiler,
            final RegexpFactory regexpFactory) {
        this.storage = new NodeStorageImpl(this);
        this.compiler = checkNotNull(compiler, "Null compiler is meaningless");
        this.regexpFactory = checkNotNull(regexpFactory,
                "regexpFactory can't be null");
    }

    public NodeStorage getNodeStorage() {
        return this.storage;
    }

    @Override
    public boolean hasRegexp(final String regexp) {
        synchronized (regexps) {
            return regexps.containsKey(regexp);
        }
    }

    @Override
    public Regexp getRegexp(final String regexpString) {
        synchronized (regexps) {
            Regexp r = regexps.get(regexpString);
            if (r == null) {
                r = regexpFactory.newRegexp(regexpString);
                regexps.put(regexpString, r);
            }
            return r;
        }
    }

    @Override
    public void add(final String regexpString, final Action action)
            throws RegexpParserException {
        synchronized (regexps) {
            final Regexp r = getRegexp(regexpString);
            r.add(action);

            if (!r.isCompiled()) {
                final NDFANode n = compiler.compile(r, this);
                assert (n != null);
                r.setMyNDFANode(n);
                storage.addToStartnode(n);
            }

            Set<Character> starterChars = r.knownStarterChars();
            if (!starterChars.isEmpty()) {
                for (Character c: starterChars) {
                    if (!this.canOnlyStartAtSpecificCharsMap.containsKey(c)) {
                        this.canOnlyStartAtSpecificCharsMap.put(c, new HashSet<>());
                    }
                    this.canOnlyStartAtSpecificCharsMap.get(c).add(r);
                }
            } else {
                canStartAtAnyCharSet.add(r);
            }
        }
    }

    private final Map<Character, Set<Regexp>> canOnlyStartAtSpecificCharsMap = new HashMap<>();

    private final  Set<Regexp> canStartAtAnyCharSet = new HashSet<>();


    @Override
    public Set<Regexp> getRegexpsThatCanStartWithSpecificChar(Character ch) {
        if (this.canOnlyStartAtSpecificCharsMap.containsKey(ch)) {
            return this.canOnlyStartAtSpecificCharsMap.get(ch);
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<Regexp> getRegexpsThatCanStartWithAnyChar() {
        return canStartAtAnyCharSet;
    }


    @Override
    public void remove(final String rexp, final Action a) {
        synchronized (regexps) {
            final Regexp r = getRegexp(rexp);

            r.remove(a);
            if (!r.hasActions()) {
                regexps.remove(rexp);
            }
        }
    }

    @Override
    public Set<String> getRegexpSet() {
        synchronized (regexps) {
            return regexps.keySet();
        }
    }
}
