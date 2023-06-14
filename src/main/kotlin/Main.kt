import org.jbpt.algo.tree.rpst.IRPSTNode
import org.jbpt.algo.tree.rpst.RPST
import org.jbpt.algo.tree.tctree.TCTree
import org.jbpt.algo.tree.tctree.TCType
import org.jbpt.graph.DirectedEdge
import org.jbpt.graph.MultiDirectedGraph
import org.jbpt.hypergraph.abs.Vertex
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.IOException
import java.lang.RuntimeException
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


/**
 *  Please Read Me:
 *
 *  To run this you can either:
 *
 *
 *  1) Use a prebuilt jar available at:
 *      a) https://github.com/l-rossi/BPTM_Assignment_3a/blob/master/src/main/resources/application.jar
 *      b) In my personal directory on lehre.bpm.in.tum.de (ge93xax) (application.jar)
 *  2) Build yourself:
 *      - get the source code from https://github.com/l-rossi/BPTM_Assignment_3a (which is basically this file with a
 *        pom file for building). Alternatively: Just use the code already present in my personal directory at lehre.bpm.in.tum.de (ge93xax)
 *      - Build using maven available here: https://maven.apache.org/download.cgi
 *           - run build using `mvn package` which will place two jars in the target directory (use the one called ass5K-1.0-jar-with-dependencies.jar)
 *           - If you are on lehre.bpm.in.tum you can use the files provided in my directory by using the following
 *             commands:
 *                  - `cd /assignment_3a/BPTM_Assignment_3a`
 *                  - `../../apache-maven-3.9.2/bin/mvn package`
 *                  - `cd target`
 *                 (- rename: `mv ass5K-1.0-jar-with-dependencies.jar ass3a`  )
 *      (- make the file executable: `chmod +x ass3a`)
 *      - The file can now be run using java: `java -jar ass3a <bpmn file>`
 *      (- Optional: make runnable directly:
 *          - `echo '#! /usr/bin/java -jar' > ass3a_runnable`
 *          - `cat ass3a  >> ass3a_runnable`
 *          - `chmod +x ass3a_runnable`
 *
 */

val TASK_TYPES = setOf("serviceTask", "userTask", "intermediateCatchEvent", "task")
val BORDER_TYPES = setOf("endEvent", "startEvent")
val GATE_TYPES = setOf("exclusiveGateway", "parallelGateway")

@Throws(IOException::class)
fun main(args: Array<String>) {

    if(args.size != 1){
        println("Usage ./<filename> <bpmn>")
        return
    }

    val inputFile = File(args[0])
    // Gotta love Javas' AbstractBuilderBuilderFactoryBuilders
    val inDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputFile)


    val sequenceFlows = inDoc.getElementsByTagName("sequenceFlow")

    val relevantVertNodes = inDoc.getElementsByTagName("process").asIterable().first().childNodes.asIterable().filter {
        it.nodeName in TASK_TYPES || it.nodeName in GATE_TYPES || it.nodeName in BORDER_TYPES
    }


//    relevantVertNodes.forEach { println("${it} ${it.attributes?.getNamedItem("name")}, ${it.nodeName}") }
//    println("-----")

    // Map the id to the node to look up properties from the finished RSVP graph
    val origin = mutableMapOf<String, Node>()

    val graph = MultiDirectedGraph()

    val verticesMap = relevantVertNodes.map {
        val id = it.attributes.getNamedItem("id").nodeValue.toString()
        // I never took a functional programming class so my functions do not have to be side-effect-free :)
        origin[id] = it
        return@map id to Vertex(id, it.nodeName + ": " + it.attributes.getNamedItem("name").nodeValue.toString())
    }.toMap()

//    verticesMap.values.forEach { println("${it} ${it.description}") }

    graph.addVertices(verticesMap.values)

    sequenceFlows.asIterable().forEach() {
        val source = it.attributes.getNamedItem("sourceRef").nodeValue.toString()
        val target = it.attributes.getNamedItem("targetRef").nodeValue.toString()
        graph.addEdge(verticesMap[source], verticesMap[target])
    }

    val rpst = RPST(graph)

    val (outDoc, description) = getBoilerplatedOutput()

    val stack = Stack<Triple<IRPSTNode<DirectedEdge, Vertex>, Int, Node>>()
    stack.push(Triple(rpst.root, 0, description))

    while (stack.isNotEmpty()) {
        val (curr, depth, parentXMLNode) = stack.pop()

//        println("${"|  ".repeat((depth - 1).coerceAtLeast(0)) + "|--".repeat(depth.coerceAtMost(1))}${curr.name}, ${curr.description}, ${curr.type}, Entry: ${curr.entry?.description}, Exit: ${curr.exit?.description}")

        /*
        if c is a polygon then
            ...
            If a child component is a fragment, order the child components from entry to exit.
         */
        val children = if (curr.type == TCType.POLYGON) {
            buildRSPTChildChain(curr.entry, curr.exit, rpst.getChildren(curr))
        } else {
            rpst.getChildren(curr)
        }

        val outputNode = writeCPEENode(outDoc, parentXMLNode, curr, origin, children, rpst)

        stack.addAll(children.map { Triple(it, depth + 1, outputNode) })
    }

    initChooseProbabilities(outDoc)

    // Stolen from https://mkyong.com/java/how-to-create-xml-file-in-java-dom/#pretty-print-xml
    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    val source = DOMSource(outDoc)
    val result = StreamResult(System.out)
    transformer.transform(source, result)

}

fun initChooseProbabilities(doc: Document) {
    doc.getElementsByTagName("choose").asIterable().forEach { choose ->
        choose.childNodes.asIterable().filterIsInstance<Element>().forEachIndexed { index, child ->
            if (child.nodeName == "alternative") {

                // I wish I had paid more attention when reading Knuth's chapter about tree traversal :/
                // We are basically stepping through the tree using reversed DFS to find the most recently
                // executed call and get its return value. (As I can think of nothing better to do)

                val previousCallNode = findLastCall(choose.parentNode)
                    ?: throw RuntimeException(
                        "You somehow managed to add a condition without any previous calls. " +
                                "How is this supposed to be evaluated?"
                    ) // Escape the "choose"


                val parentId = previousCallNode.attributes.getNamedItem("id").nodeValue
                child.setAttribute(
                    "condition",
                    "data.$parentId < ${index + 1}/${choose.childNodes.length} && data.$parentId >= ${index}/${choose.childNodes.length}"
                )
            }
        }
    }



    doc.getElementsByTagName("loop").asIterable().filterIsInstance<Element>().forEach { loop ->
        val mode = loop.attributes.getNamedItem("mode").nodeValue

        val lastCall = if (mode == "pre_test") {
            findLastCall(loop)
        } else {
            findLastCall(loop.childNodes.asIterable().last())
        } ?: throw RuntimeException("Could not find any possible decision data for a loop.")

        val callId = lastCall.attributes.getNamedItem("id").nodeValue
        loop.setAttribute(
            "condition",
            "data.$callId < 2/3"
        )
    }
}

fun findLastCall(from: Node): Node? {
    var loopDetectionCounter = 0
    var running: Node? = from

    while (running != null && running.nodeName != "call") {
        running = if (running.previousSibling != null) {
            running.previousSibling
        } else {
            running.parentNode
        }

        if (++loopDetectionCounter > 100_000_000) {
            throw RuntimeException("I have encountered what is probably an endless loop whilst searching for call parents of a decision.")
        }
    }

    return running
}


fun writeCPEENode(
    doc: Document,
    parent: Node,
    node: IRPSTNode<DirectedEdge, Vertex>,
    origin: Map<String, Node>,
    children: Collection<IRPSTNode<DirectedEdge, Vertex>>,
    rpst: RPST<DirectedEdge, Vertex>
): Node {

    // Is it a bond?
    //      -> Yes (XOR/Parallel/Loop): Are entry and exits parallel?
    //              -> Yes: Parallel
    //              -> No (XOR/loop): Does it have an "edge" going from the exit to the entry?
    //                  -> Yes: Loop (Head/Tail controlled depending on the origin nodes incoming/outgoing behaviour)
    //                  -> No:  XOR
    //      -> No: Sequence


    if (node.type == TCType.BOND) {
        // XOR/Parallel/Loop
        val entryOrigin = origin[node.entry.name]
        val exitOrigin = origin[node.exit.name]

        if (entryOrigin == null || entryOrigin.nodeName != exitOrigin?.nodeName) {
            throw RuntimeException("Your BPMN file makes no sense (or maybe my program doesn't. That is actually more likely...)")
        }



        if (entryOrigin.nodeName == "parallelGateway") {
            // Parallel
            return parent.appendChild(
                doc.createElement("parallel").also {
                    it.setAttribute("wait", "-1")
                    it.setAttribute("cancel", "last")
                }
            )


        } else if (entryOrigin.nodeName == "exclusiveGateway") {
            // XOR or loop

            val backEdge = children.find {
                it.entry == node.exit && it.exit == node.entry
            }

            if (backEdge != null) {
                // loop
                val o = origin[node.entry.name]
                val mode =
                    when (val direction = o?.attributes?.getNamedItem("gatewayDirection")?.nodeValue.toString()) {
                        "Converging" -> "post_test"
                        "Diverging" -> "pre_test"
                        else -> throw RuntimeException("Found exclusive gate with direction '$direction'. Expected either 'Diverging' or 'Converging'")
                    }

                return parent.appendChild(doc.createElement("loop").also {
                    it.setAttribute("mode", mode)
                }).also {
                    // No idea what this is
//                    val prob = it.appendChild(doc.createElement("_probability"))
//                    prob.appendChild(doc.createElement("_probability_min"))
//                    prob.appendChild(doc.createElement("_probability_max"))
//                    prob.appendChild(doc.createElement("_probability_avg")).textContent = "3"
                }

            } else {
                // XOR
                return parent.appendChild(
                    doc.createElement("choose").also {
                        // We do not support inclusive gateways
                        it.setAttribute("mode", "exclusive")
                    }
                )
            }
        } else {
            throw RuntimeException("You have defined a node with type ${entryOrigin.nodeName} which is not allowed.")
        }

    } else if (node.type == TCType.POLYGON) {
        // Sequence

        // I think this is technically not quite correct:
        // I think it maps roughly to
        // "if c is not a fragment and c has at least two child fragments then
        //      Create a maximal sequence (that contains a proper subset of children of c)."

        return when (parent.nodeName) {
            "parallel" -> parent.appendChild(doc.createElement("parallel_branch").also {
                it.setAttribute("pass", "")
                it.setAttribute("local", "")
            })

            "choose" -> parent.appendChild(doc.createElement("alternative"))
            "loop" -> parent
            "description" -> parent.appendChild(doc.createElement("description").also {
                it.setAttribute("xmlns", "http://cpee.org/ns/description/1.0")
            })

            else -> throw RuntimeException("Unexpected parent node type of ${parent.nodeName}.")

        }

    } else if (node.type == TCType.TRIVIAL) {
        // Single Call

        val o = origin[node.entry.name]

        val actualParent = rpst.getParent(node)
        if (node.exit == actualParent.exit && node.entry == actualParent.entry && !parent.childNodes.asIterable()
                .any { it.nodeName == "otherwise" }
        ) {
            return parent.appendChild(
                doc.createElement("otherwise")
            )
        }

        if (o?.nodeName !in TASK_TYPES) {
            return parent
        }

        return parent.appendChild(doc.createElement("call").also {
            val id = "id_${UUID.randomUUID().toString().replace("-", "_")}"
            it.setAttribute("endpoint", getEndpoint(o))
            it.setAttribute("id", id)
            appendBloatToCalls(
                doc,
                it,
                o?.attributes?.getNamedItem("name")?.nodeValue.toString(),
                "1.1",
                "data.${id} = rand()"
            )
        })
    } else {
        throw RuntimeException("Node type ${node.type} is not supported.")
    }
}


fun appendBloatToCalls(doc: Document, callNode: Node, label: String?, timeout: String, finalize: String) {
    val parameters = callNode.appendChild(doc.createElement("parameters"))
    parameters.appendChild(doc.createElement("label")).textContent = label
    val arguments = parameters.appendChild(doc.createElement("arguments"))
    arguments.appendChild(doc.createElement("timeout")).textContent = timeout

    val code = callNode.appendChild(doc.createElement("code"))
    code.appendChild(doc.createElement("prepare"))
    code.appendChild(doc.createElement("finalize").also {
        it.setAttribute("output", "result")
        it.textContent = finalize
    })
    code.appendChild(doc.createElement("update").also {
        it.setAttribute("output", "result")
    })
    code.appendChild(doc.createElement("rescue").also {
        it.setAttribute("output", "result")
    })

    val annotations = callNode.appendChild(doc.createElement("annotations"))
    annotations.appendChild(doc.createElement("_timing")).also {
        it.appendChild(doc.createElement("_timing_weight"))
        it.appendChild(doc.createElement("_timing_avg"))
        it.appendChild(doc.createElement("explanations"))
    }

    annotations.appendChild(doc.createElement("_shifting"))
        .appendChild(doc.createElement("_shifting_type")).textContent = "Duration"


    annotations.appendChild(doc.createElement("_context_data_analysis")).also {
        it.appendChild(doc.createElement("probes"))
        it.appendChild(doc.createElement("ips"))
    }

    annotations.appendChild(doc.createElement("report")).appendChild(doc.createElement("url"))

    annotations.appendChild(doc.createElement("_notes")).appendChild(doc.createElement("_notes_general"))


    callNode.appendChild(doc.createElement("documentation")).also {
        it.appendChild(doc.createElement("input"))
        it.appendChild(doc.createElement("output"))
        it.appendChild(doc.createElement("implementation")).appendChild(doc.createElement("description"))
    }


}

fun getEndpoint(node: Node?): String? {
    return when (node?.nodeName) {
        "userTask" -> "user"
        "serviceTask" -> "auto"
        "intermediateCatchEvent" -> "timeout"
        "task" -> "auto"
        else -> null // No idea what the default should be
    }

}

fun getBoilerplatedOutput(): Pair<Document, Node> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

    val root = doc.createElement("testset")
    root.setAttribute("xmlns", "http://cpee.org/ns/properties/2.0")
    doc.appendChild(root)

    root.appendChild(doc.createElement("executionhandler")).textContent = "ruby"
    root.appendChild(doc.createElement("dataelements"))

    val endpoints = root.appendChild(doc.createElement("endpoints"))
    endpoints.appendChild(doc.createElement("timeout")).textContent = "http://cpee.org/services/timeout.php"
    endpoints.appendChild(doc.createElement("subprocess")).textContent = "https-post://cpee.org/flow/start/url"
    endpoints.appendChild(doc.createElement("send")).textContent = "https-post://cpee.org/ing/correlators/message/send"
    endpoints.appendChild(doc.createElement("receive")).textContent =
        "https-get://cpee.org/ing/correlators/message/receive"
    endpoints.appendChild(doc.createElement("user")).textContent = "https-post://cpee.org/services/timeout-user.php"
    endpoints.appendChild(doc.createElement("auto")).textContent = "https-post://cpee.org/services/timeout-auto.php"

    val attributes = root.appendChild(doc.createElement("attributes"))
    attributes.appendChild(doc.createElement("guarded")).textContent = "none"
    // TODO maybe don't hardcode
    attributes.appendChild(doc.createElement("info")).textContent = "Building A Treehouse"
    attributes.appendChild(doc.createElement("modeltype")).textContent = "CPEE"
    attributes.appendChild(doc.createElement("theme")).textContent = "extended"
    attributes.appendChild(doc.createElement("guarded_id"))

    val description = root.appendChild(doc.createElement("description"))


    val transformation = root.appendChild(doc.createElement("transformation"))
    transformation.appendChild(doc.createElement("description").also {
        it.setAttribute("type", "copy")
    })
    transformation.appendChild(doc.createElement("dataelements").also {
        it.setAttribute("type", "none")
    })
    transformation.appendChild(doc.createElement("endpoints").also {
        it.setAttribute("type", "none")
    })

    return Pair(doc, description)
}


fun buildRSPTChildChain(
    entry: Vertex, exit: Vertex, nodes: Set<IRPSTNode<DirectedEdge, Vertex>>
): List<IRPSTNode<DirectedEdge, Vertex>> {
    if (nodes.isEmpty()) {
        return emptyList()
    }

    val chain = mutableListOf<IRPSTNode<DirectedEdge, Vertex>>()

    chain.add(nodes.find { it.exit == exit }!!)

    while (chain.last().entry != entry) {
        chain.add(nodes.find { it.exit == chain.last().entry }!!)
    }

    return chain
}


fun NodeList.asIterable(): Iterable<Node> = object : Iterable<Node> {
    override fun iterator(): Iterator<Node> {
        return this@asIterable.iterator()
    }
}

fun Node.findChildRecursively(predicate: (node: Node) -> Boolean): Node? {
    val stack = Stack<Node>()
    stack.add(this)
    while (stack.isNotEmpty()) {
        val curr = stack.pop()
        if (predicate(curr)) {
            return curr
        }
        stack.addAll(curr.childNodes.asIterable())
    }

    return null
}

operator fun NodeList.iterator(): Iterator<Node> {
    var curr = 0;

    return object : Iterator<Node> {
        override fun hasNext(): Boolean {
            return curr < this@iterator.length
        }

        override fun next(): Node {
            return this@iterator.item(curr++)
        }
    }
}

// Example from paper

//
//    val s = Vertex("s")
//    val t = Vertex("t")
//    val v = (1..7).map {
//        Vertex("v$it")
//    }
//
//
//    val g = MultiDirectedGraph()
//    g.addVertex(Vertex("s"))
//    v.forEach {
//        g.addVertex(it)
//    }
//    g.addVertex(Vertex("t"))
//
//
//
//    g.addEdge(s, v[0]).name = "a" // a
//    g.addEdge(s, v[1]).name = "b" // b
//    g.addEdge(v[0], v[2]).name = "c" // c
//    g.addEdge(v[2], v[1]).name = "d"  // d
//    g.addEdge(v[2], v[3]).name = "e" // e
//    g.addEdge(v[3], v[0]).name = "f" // f
//    g.addEdge(v[3], v[1]).name = "g" // g
//    g.addEdge(v[0], v[4]).name = "h" // h
//    g.addEdge(v[1], v[4]).name = "i" // i
//    g.addEdge(v[4], v[5]).name = "j" // j
//    g.addEdge(v[5], v[4]).name = "k" // k
//    g.addEdge(v[5], v[6]).name = "l" // l
//    g.addEdge(v[4], v[6]).name = "m" // m
//    g.addEdge(v[6], v[4]).name = "n" // n
//    g.addEdge(v[5], t).name = "o" // o
//
//
//    val rpst = RPST(g)
//