import org.jbpt.algo.tree.rpst.RPST
import org.jbpt.algo.tree.rpst.RPSTNode
import org.jbpt.algo.tree.tctree.TCTreeNode
import org.jbpt.graph.DirectedEdge
import org.jbpt.graph.abs.IDirectedEdge
import org.jbpt.hypergraph.abs.IVertex
import org.jbpt.hypergraph.abs.Vertex

class SubparRPSTNode<E : IDirectedEdge<V>, V : IVertex>(rpst: RPST<E, V>, tcnode: TCTreeNode<DirectedEdge, Vertex>) :
    RPSTNode<E, V>(rpst, tcnode) {
}