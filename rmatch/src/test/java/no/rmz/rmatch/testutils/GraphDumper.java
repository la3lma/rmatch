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

package no.rmz.rmatch.testutils;

import static com.google.common.base.Preconditions.checkNotNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.impls.DFANodeImpl;
import no.rmz.rmatch.interfaces.DFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.NodeStorage;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * Utility class to dump the content of a graph as a Graphviz file.
 */
public final class GraphDumper {

    /**
     * Our log.
     */
    private static final Logger LOG =
            Logger.getLogger(GraphDumper.class.getName());
    /**
     * The filename that will be used for ndfa files.
     */
    public static final String NDFA_ENDING = "ndfa.dot";
    /**
     * The filename that will be used for dfa files.
     */
    public static final String DFA_ENDING = "dfa.dot";

    /**
     * True iff the DFANOde in question is terminal for any
     * regexp.
     * @param dfanode a node
     * @return true iff it's termial for some regexp.
     */
    private static boolean isTerminalForAnyRegexp(final DFANode dfanode) {
        // XXX This could be cached in the DFANOde, but is it worth it?
        checkNotNull(dfanode);
        for (final Regexp r : dfanode.getRegexps()) {
            if (dfanode.isTerminalFor(r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Utility class, shouldn't be able to create instances.
     */
    private GraphDumper() {
    }

    /**
     * Utility method to get an unique print representation for the name of an
     * NDFA node.
     *
     * @param node the node.
     * @return the string naming the node uniquely.
     */
    private static String nodeName(final NDFANode node) {
        checkNotNull(node);
        final AbstractNDFANode an = (AbstractNDFANode) node;
        return "ND_" + an.getId();
    }

    /**
     * Dump a collection of NDFA nodes to a PrintStream as GraphViz code.
     * @param ndfaNodes the ndoes.
     * @param out the PrintStream to write GraphViz code to.
     */
    static void dumpNdfa(
            final Collection<NDFANode> ndfaNodes,
            final PrintStream out) {
        checkNotNull(ndfaNodes);
        checkNotNull(out);

        out.println("digraph nondeterminstic_finite_state_machine {");
        out.println("rankdir=LR");
        out.println("size=\"8,5\"");

        out.println("node [shape = doublecircle ];");
        for (final NDFANode ndfanode : ndfaNodes) {
            if (ndfanode.isTerminal() && !ndfanode.isFailing()) {
                out.printf("   %s\n", nodeName(ndfanode));
            }
        }

        out.println("node [shape = doubleoctagon ];");
        for (final NDFANode ndfanode : ndfaNodes) {
            if (ndfanode.isTerminal() && ndfanode.isFailing()) {
                out.printf("   %s\n", nodeName(ndfanode));
            }
        }

        out.println("node [shape = Mcircle ];");
        for (final NDFANode ndfanode : ndfaNodes) {
            if (ndfanode.isFailing()) {
                out.printf("   %s\n", nodeName(ndfanode));
            }
        }

        out.println("node [shape = circle ];");

        for (final NDFANode ndfanode : ndfaNodes) {
            if (!ndfanode.isTerminal()) {
                out.printf("   %s\n", nodeName(ndfanode));
            }
        }

        for (final NDFANode source : ndfaNodes) {
            for (final PrintableEdge edge : source.getEdgesToPrint()) {
                if (edge.getLabel() == null) {
                    printEpsilonEdge(out, source, edge.getDestination());
                } else {
                    printLabeledEdge(
                            out,
                            source,
                            edge.getDestination(),
                            edge.getLabel());
                }
            }

        }
        out.println("}");
    }

    /**
     * Dump a representation of an epsilon edge that is parsable by
     * GraphViz.
     *
     * @param out The PrintStream to dump the result to.
     * @param source The start node of the epsilon edge.
     * @param destination the end node of the epsilon edge.
     */
    private static void printEpsilonEdge(
            final PrintStream out,
            final NDFANode source,
            final NDFANode destination) {
        checkNotNull(out);
        checkNotNull(source);
        checkNotNull(destination);
        out.printf("%s -> %s [label = \"&epsilon;\" ]; \n",
                nodeName(source), nodeName(destination));
    }

    /**
     * Print an edge with a label, e.g. "x" or "[a-b]", representing
     * the character that must be matches for this edge to be followed.
     * @param out The PrintStream to dump the GraphViz code to.
     * @param source The source node of the edge.
     * @param destination The destination node of the edge.
     * @param label The label to put on the edge.
     */
    private static void printLabeledEdge(
            final PrintStream out,
            final NDFANode source,
            final NDFANode destination,
            final String label) {
        checkNotNull(out);
        checkNotNull(source);
        checkNotNull(destination);
        checkNotNull(label);
        out.printf("%s -> %s [label = \"%s\" ]; \n",
                nodeName(source),
                nodeName(destination),
                label);
    }

    /**
     * Dump the automata (determinstic and not) produced during the running
     * of a test to  a file in the target/nodeDumps directory.
     * @param nameOfTest Then name of the test.
     * @param nodeStorage The nodes to dump.
     */
    public static void dump(
            final String nameOfTest,
            final NodeStorage nodeStorage) {
        checkNotNull(nodeStorage);
        checkNotNull(nameOfTest);

        final String baseDumpDirectoryName = "target/nodeDumps/";
        final String dumpDiretoryName =
                baseDumpDirectoryName + nameOfTest + "/";

        final File dumpDir = new File(dumpDiretoryName);
        if (!dumpDir.exists() || !dumpDir.isDirectory()) {
            if (!dumpDir.exists()) {
                boolean mkdirs = dumpDir.mkdirs();
                if (!mkdirs) {
                    throw new RuntimeException("Couldn't make directory: "
                            + dumpDiretoryName);
                }
            } else {
                throw new IllegalStateException(
                        "The path to the dump directory exists, "
                        + " but it's not pointing to a directory"
                        + dumpDiretoryName);
            }
        }
        // At this point we have a dump directory

        final File dumpFileForNdfa = new File(dumpDiretoryName + NDFA_ENDING);
        final File dumpFileForDfa = new File(dumpDiretoryName + DFA_ENDING);

        final PrintStream psForNdfa;
        final PrintStream psForDfa;
        try {
            psForNdfa = new PrintStream(dumpFileForNdfa);
            psForDfa = new PrintStream(dumpFileForDfa);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }

        dumpNdfa(nodeStorage.getNDFANodes(), psForNdfa);
        dumpDfa(nodeStorage.getDFANodes(), psForDfa);

        psForDfa.close();
        psForNdfa.close();
    }

    /**
     * Dump all nodes  and the edges between them as graphviz representations
     * to the "out" PrintStream.
     * @param nodeStorage A set of nodes to dump.
     * @param out the PrintSteram to dump a GraphViz representation to.
     */
    public static void dump(
            final NodeStorage nodeStorage,
            final PrintStream out) {
        checkNotNull(nodeStorage);
        checkNotNull(out);
        dumpNdfa(nodeStorage.getNDFANodes(), out);
        dumpDfa(nodeStorage.getDFANodes(), out);
    }

    /**
     * Dump all the nodes, both dfa and ndfa, to a printsteam representing
     * the nodes and the edges between them as a GraphViz graph.
     * @param dfaNodes A collection of DFA nodes.
     * @param ndfaNodes A collection of NDFA nodes.
     * @param out a PrintSteam with GraphViz syntax.
     */
    public static void dump(
            final Collection<DFANode> dfaNodes,
            final Collection<NDFANode> ndfaNodes,
            final PrintStream out) {
        checkNotNull(dfaNodes);
        checkNotNull(ndfaNodes);
        checkNotNull(out);
        dumpNdfa(ndfaNodes, out);
        dumpDfa(dfaNodes, out);
    }

    /**
     * Given a DFANode, generate a name for it that will be used as
     * the nodename in the Graphviz representation.
     * @param dfanode the node.
     * @return The name.
     */
    private static String nodeName(final DFANode dfanode) {
        checkNotNull(dfanode);
        final DFANodeImpl i = (DFANodeImpl) dfanode;
        return "D_" + i.getId();
    }

    /**
     * Dump a collectionof DFA nodes to a PrintStream.
     * @param dfaNodes the nodes.
     * @param out The PrintStream.
     */
    private static void dumpDfa(
            final Collection<DFANode> dfaNodes,
            final PrintStream out) {
        checkNotNull(dfaNodes);
        checkNotNull(out);
        out.println("digraph determinstic_finite_state_machine {");
        out.println("rankdir=LR");
        out.println("size=\"8,5\"");

        out.println("node [shape = doublecircle ];");
        for (final DFANode dfanode : dfaNodes) {
            if (isTerminalForAnyRegexp(dfanode)) {
                out.printf("   %s\n", nodeName(dfanode));
            }
        }

        out.println("node [shape = circle ];");
        for (final DFANode dfanode : dfaNodes) {
            if (!isTerminalForAnyRegexp(dfanode)) {
                out.printf("   %s\n", nodeName(dfanode));
            }
        }

        for (final DFANode source : dfaNodes) {
            final Map<Character, DFANode> nextMap =
                    ((DFANodeImpl) source).getNextMap();
            for (final Entry<Character, DFANode> entry : nextMap.entrySet()) {

                out.printf("%s -> %s [label = \"%c\" ]; \n",
                        nodeName(source),
                        nodeName(entry.getValue()),
                        entry.getKey());
            }
        }
        out.println("}");
    }
}
