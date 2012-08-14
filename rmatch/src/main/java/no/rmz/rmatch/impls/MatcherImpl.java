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

import static com.google.common.base.Preconditions.checkNotNull;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.MatchEngine;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.NodeStorage;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.interfaces.RegexpStorage;

/**
 * An implementation ofthe Matcher interface that hooks together
 * various component that together makes up a real matcher.
 */
public final class MatcherImpl implements Matcher {

    /**
     * The compiler we will use.
     */
    private final NDFACompiler compiler;

    /**
     * The regexp storage we will use.
     */
    private final RegexpStorage rs;

    /**
     * Our prescious MatchEngine.
     */
    private final MatchEngine me;

    /**
     * Our equally prescious NodeStorage. Our preciousss.
     */
    private final NodeStorage ns;

    /**
     * Create a new matcher using the default compiler and regexp
     * factory.  This is usually a good choice for production use.
     */
    public MatcherImpl() {
        this(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
    }



    /**
     * This is useful for testing when we sometimes want to
     * inject mocked compilers and regexp factories.
     *
     * @param compiler A compiler to use.
     * @param regexpFactory A regexp factory to use.
     */
    public MatcherImpl(
            final NDFACompiler compiler,
            final RegexpFactory regexpFactory) {
        this.compiler = checkNotNull(compiler);
        checkNotNull(regexpFactory);
        ns = new NodeStorageImpl();
        rs = new RegexpStorageImpl(ns, compiler, regexpFactory);
        me = new MatchEngineImpl(ns);
    }

    @Override
    public void add(final String r, final Action a)
            throws RegexpParserException {
        synchronized (rs) {
            rs.add(r, a);
        }
    }

    @Override
    public void remove(final String r, final Action a) {
        synchronized (rs) {
            rs.remove(r, a);
        }
    }

    @Override
    public void match(final Buffer b) {
        synchronized (me) {
            me.match(b);
        }
    }

    @Override
    public NodeStorage getNodeStorage() {
        return ns;
    }

    @Override
    public void shutdown() throws InterruptedException {
    }
}
