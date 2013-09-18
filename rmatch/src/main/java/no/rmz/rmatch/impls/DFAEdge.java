/*
 * Copyright 2013 Rmz.
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
package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.*;

/**
 * Represent an edge going from one DFA node to another, or possibly
 * nowhere.
 */
public final class DFAEdge {

    private static final DFAEdge NULLEDGE = new DFAEdge(null);

    public static final DFAEdge newEdge(final DFANode target) {
        if (target == null) {
            return NULLEDGE;
        } else {
            return new DFAEdge(target);
        }
    }
    
    final DFANode target;

    private DFAEdge(final DFANode target) {
        this.target = target;
    }

    public DFANode getTarget() {
        return target;
    }

    public boolean leadsNowhere() {
        return target == null;
    }
}
