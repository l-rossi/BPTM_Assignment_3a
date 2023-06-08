import org.jbpt.algo.tree.rpst.IRPST
import org.jbpt.algo.tree.rpst.IRPSTNode
import org.jbpt.algo.tree.rpst.RPST
import org.jbpt.algo.tree.rpst.RPSTNode
import org.jbpt.algo.tree.tctree.TCTree
import org.jbpt.algo.tree.tctree.TCTreeNode
import org.jbpt.algo.tree.tctree.TCType
import org.jbpt.graph.DirectedEdge
import org.jbpt.graph.MultiDirectedGraph
import org.jbpt.graph.abs.AbstractTree
import org.jbpt.graph.abs.IDirectedEdge
import org.jbpt.graph.abs.IDirectedGraph
import org.jbpt.hypergraph.abs.IVertex
import org.jbpt.hypergraph.abs.Vertex
import java.util.*

/**
 *
 * This class badly implements Algorithm 1 of
 * Vanhatalo, Jussi & Völzer, Hagen & Koehler, Jana. (2009). The refined process structure tree.. Data Knowl. Eng.. 68. 793-818.
 *
 * Unsurprisingly, this implementation follows that of the RPST in the jbpt library very closely.
 *
 * {@link RPST} sadly implements an improved version and can thus not be simply stolen
 *
 */
class SubparRPST<E : IDirectedEdge<V>, V : IVertex>(graph: IDirectedGraph<E, V>) : RPST<E, V>(graph) {

    private val diGraph: IDirectedGraph<E, V> = graph
    private var normalizedGraph: MultiDirectedGraph? = null

    private val normalizedEdgeToOriginalEdge: MutableMap<DirectedEdge, E> = mutableMapOf()
    private val originalVertToNormalizedVert: MutableMap<V, Vertex> = mutableMapOf()

    private val extraEdges: MutableSet<DirectedEdge> = mutableSetOf()
    private var backEdge: DirectedEdge? = null


    init {
        if (!graph.edges.isEmpty()) {
            this.normalizeGraph()
            this.constructRPST()
        }
    }


    override fun getGraph(): IDirectedGraph<E, V> {
        throw NotImplementedError("I do not need this so I did not implement it.")
    }

    override fun getRPSTNodes(p0: TCType?): MutableSet<IRPSTNode<E, V>> {
        throw NotImplementedError("I do not need this so I did not implement it.")
    }

    override fun getRPSTNodes(): MutableSet<IRPSTNode<E, V>> {
        throw NotImplementedError("I do not need this so I did not implement it.")
    }

    override fun getPolygonChildren(p0: IRPSTNode<E, V>?): MutableList<IRPSTNode<E, V>> {
        throw NotImplementedError("I do not need this so I did not implement it.")
    }

    private fun constructRPST() {
        val spqrTree = TCTree(this.normalizedGraph, this.backEdge)

        // Entry / exit node detection is done by the RPST node and does not have to be done here
        // Ordering is also done in buildRSPTChildChain instead of here

        // parent to to-be-merged children
        val toBeMerged: MutableMap<TCTreeNode<DirectedEdge, Vertex>, List<TCTreeNode<DirectedEdge, Vertex>>> =
            mutableMapOf()


        // for each Component c in Tree in a post-order of a depth-first traversal do
        dfsPostOrderApply(spqrTree, spqrTree.root) {

            val createdInThisIteration = mutableListOf<TCTreeNode<DirectedEdge, Vertex>>()

            if (it.type == TCType.POLYGON) {
                val toBeMergedHere = toBeMerged[it] ?: emptyList()
                this.merge(spqrTree, toBeMergedHere)
                // TODO if c is not a fragment and c has at least two child fragments then
                //     Create a maximal sequence (that contains a proper subset of children of c).

            }else if(it.type == TCType.BOND){
                // Deferred to caller: Classify each branch of c based on the edge counts of the boundary nodes of the respective child components of c.

                // ???
                val isFragment = false
                if(isFragment){

                }else{

                }


            }


//            // if c is a polygon then
//            if (it.type == TCType.POLYGON) {
//                // Merge consecutive child components (that are not fragments if any exist) if those form a minimal child fragment
//                val children = spqrTree.getChildren(it)
//                if (children.isNotEmpty()) {
//                    val first = children.first()
//
//                    for (child in spqrTree.getChildren(it)) {
//                        if (child == first) {
//                            continue
//                        }
//                        for (edge in child.skeleton.originalEdges) {
//                            first.skeleton.originalEdges.add(first.skeleton.addEdge(edge.v1, edge.v2))
//                        }
//                        for (edge in child.skeleton.virtualEdges) {
//                            first.skeleton.virtualEdges.add(first.skeleton.addEdge(edge.v1, edge.v2))
//                        }
//
//                        for (vert in child.skeleton.vertices) {
//                            first.skeleton.addVertex(vert)
//                        }
//                    }
//
//                    spqrTree.getChildren(it)
//                        .drop(1)
//                        .forEach { child ->
//                            spqrTree.removeVertex(child)
//                        }
//
//                    // This is also done in the
//                    // if c is not a fragment and c has at least two child fragments then
//                    //     Create a maximal sequence (that contains a proper subset of children of c).
//
//                }else if(it.type == TCType.BOND){
//
//                }
//            }


        }






        // Copied from RPST
        // construct RPST nodes

        // construct RPST nodes
        val t2r: MutableMap<TCTreeNode<DirectedEdge, Vertex>, RPSTNode<E, V>> = HashMap()

        if (spqrTree.edges.isEmpty()) {
            this.root = SubparRPSTNode(this, spqrTree.vertices.first())
            addVertex(this.root)
        } else {
            for (edge in this.tctree.edges) {
                val src = edge.source
                val tgt = edge.target

                // ignore extra edges
                if (tgt.type == TCType.TRIVIAL && tgt.skeleton.originalEdges.isEmpty()) continue
                var rsrc = t2r[src]
                var rtgt = t2r[tgt]
                if (rsrc == null) {
                    rsrc = SubparRPSTNode(this, src)
                    t2r[src] = rsrc
                }
                if (rtgt == null) {
                    rtgt = SubparRPSTNode(this, tgt)
                    if (rtgt.type == TCType.TRIVIAL) {
                        rtgt.name = rtgt.fragment.toString()
                    }
                    t2r[tgt] = rtgt
                }
                if (this.tctree.isRoot(src)) this.root = rsrc
                if (this.tctree.isRoot(tgt)) this.root = rtgt
                this.addEdge(rsrc, rtgt)
            }
        }
    }

    private fun merge(tree: TCTree<DirectedEdge, Vertex>, list: List<TCTreeNode<DirectedEdge, Vertex>>) {
        if (list.isNotEmpty()) {
            val first = list.first()

            for (child in list) {
                if (child == first) {
                    continue
                }
                for (edge in child.skeleton.originalEdges) {
                    first.skeleton.originalEdges.add(first.skeleton.addEdge(edge.v1, edge.v2))
                }
                for (edge in child.skeleton.virtualEdges) {
                    first.skeleton.virtualEdges.add(first.skeleton.addEdge(edge.v1, edge.v2))
                }

                for (vert in child.skeleton.vertices) {
                    first.skeleton.addVertex(vert)
                }
            }

            list.drop(1)
                .forEach { child ->
                    tree.removeVertex(child)
                }
        }
    }


    private fun dfsPostOrderApply(
        tree: TCTree<DirectedEdge, Vertex>,
        root: TCTreeNode<DirectedEdge, Vertex>,
        consumer: (node: TCTreeNode<DirectedEdge, Vertex>) -> Unit
    ) {
        tree.getChildren(root).forEach {
            dfsPostOrderApply(tree, it, consumer)
        }

        consumer(root)
    }


    private fun normalizeGraph() {
        // This is taken straight from the original RPST class

        val localNormalizedGraph = MultiDirectedGraph()
        val sources: MutableCollection<V> = ArrayList()
        val sinks: MutableCollection<V> = ArrayList()
        val mixed: MutableCollection<V> = ArrayList()

        // copy vertices
        for (v in this.diGraph.vertices) {
            if (this.diGraph.getIncomingEdges(v).isEmpty() && this.diGraph.getOutgoingEdges(v).isEmpty()) continue
            if (this.diGraph.getIncomingEdges(v).isEmpty()) sources.add(v)
            if (this.diGraph.getOutgoingEdges(v).isEmpty()) sinks.add(v)
            if (this.diGraph.getIncomingEdges(v).size > 1 && this.diGraph.getOutgoingEdges(v).size > 1) mixed.add(v)
            this.originalVertToNormalizedVert[v] = localNormalizedGraph.addVertex(Vertex(v.name))
        }

        // copy edges
        for (e in this.diGraph.edges) this.normalizedEdgeToOriginalEdge[localNormalizedGraph.addEdge(
            this.originalVertToNormalizedVert[e.source],
            this.originalVertToNormalizedVert[e.target]
        )] = e

        // introduce single source
        val src = Vertex("SRC")
        for (v in sources) this.extraEdges.add(localNormalizedGraph.addEdge(src, this.originalVertToNormalizedVert[v]))

        // introduce single sink
        val snk = Vertex("SNK")
        for (v in sinks) this.extraEdges.add(localNormalizedGraph.addEdge(this.originalVertToNormalizedVert[v], snk))

        // split mixed 'gateways', i.e., vertices with multiple inputs and outputs
        for (v in mixed) {
            val vertex = Vertex(v.name + "*")
            for (edge in localNormalizedGraph.getIncomingEdges(this.originalVertToNormalizedVert[v])) {
                localNormalizedGraph.removeEdge(edge)
                val e: E = this.normalizedEdgeToOriginalEdge.remove(edge)!!
                val ee = localNormalizedGraph.addEdge(this.originalVertToNormalizedVert[e.source], vertex)
                this.normalizedEdgeToOriginalEdge[ee] = e
            }
            this.extraEdges.add(localNormalizedGraph.addEdge(vertex, this.originalVertToNormalizedVert[v]))
        }

        val backEdge = localNormalizedGraph.addEdge(snk, src)
        this.backEdge = backEdge
        this.extraEdges.add(backEdge)
        this.normalizedGraph = localNormalizedGraph
    }


}