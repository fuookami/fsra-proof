package com.wintelia.fuookami.fsra.domain.bunch_generation_context.model

import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

sealed class Node(val index: UInt64) {
    companion object {
        internal val root: UInt64 = UInt64.zero
        internal val end: UInt64 = UInt64.maximum
    }

    abstract val time: Instant
}

object RootNode: Node(root) {
    override val time = Instant.DISTANT_PAST
}

object EndNode: Node(end) {
    override val time = Instant.DISTANT_FUTURE
}

class TaskNode(
    val task: FlightTask,
    override val time: Instant,
    index: UInt64
): Node(index)

data class Edge(
    val from: Node,
    val to: Node
)

class Graph(
    val nodes: MutableMap<UInt64, Node> = HashMap(),
    val edges: MutableMap<Node, MutableSet<Edge>> = HashMap()
) {
    fun put(node: Node) {
        nodes[node.index] = node
    }

    fun put(from: Node, to: Node) {
        if (!edges.containsKey(from)) {
            edges[from] = HashSet()
        }
        edges[from]!!.add(Edge(from, to))
    }

    operator fun get(index: UInt64): Node? {
        return nodes[index]
    }

    operator fun get(node: Node): Set<Edge> {
        return edges[node]?: emptySet()
    }

    fun connected(from: Node, to: Node): Boolean {
        return edges[from]?.contains(Edge(from, to)) ?: false
    }
}
