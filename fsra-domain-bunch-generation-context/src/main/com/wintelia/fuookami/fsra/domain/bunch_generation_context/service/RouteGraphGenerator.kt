package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import kotlin.time.Duration.Companion.minutes
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.model.*

typealias FeasibilityJudger = (Aircraft, FlightTask?, FlightTask) -> Boolean

class RouteGraphGenerator(
    private val reverse: FlightTaskReverse,
    private val feasibilityJudger: FeasibilityJudger
) {
    operator fun invoke(
        aircraft: Aircraft,
        aircraftUsability: AircraftUsability,
        flightTasks: Map<Airport, List<FlightTask>>
    ): Result<Graph, Error> {
        val graph = Graph()
        graph.put(RootNode)
        graph.put(EndNode)
        val location = aircraftUsability.location
        val nodes = arrayListOf<Pair<Airport, Node>>(Pair(location, RootNode))
        val nodeMap = HashMap<FlightTask, Node>()
        // BFS
        while (nodes.isNotEmpty()) {
            val (airport, node) = nodes.first()
            nodes.removeFirst()

            if (!flightTasks.containsKey(airport)) {
                graph.put(node, EndNode)
                continue
            }
            searchAndInsertFlightTasks(graph, nodes, nodeMap, node, aircraft, flightTasks[airport]!!)
        }
        searchAndInsertReverseFlightTasks(graph, nodeMap, aircraft)
        return Ok(graph)
    }

    private fun searchAndInsertFlightTasks(
        graph: Graph,
        nodes: MutableList<Pair<Airport, Node>>,
        nodeMap: MutableMap<FlightTask, Node>,
        node: Node,
        aircraft: Aircraft,
        flightTasks: List<FlightTask>
    ) {
        if (node is RootNode) {
            var flag = false
            for (flightTask in flightTasks) {
                if (feasibilityJudger(aircraft, null, flightTask)) {
                    flag = true
                    insertFlightTask(graph, nodes, nodeMap, node, flightTask)
                }
            }
            if (!flag) {
                graph.put(node, EndNode)
            }
        } else {
            assert(node is TaskNode)
            val prevFlightTask = (node as TaskNode).task
            for (flightTask in flightTasks) {
                if (feasibilityJudger(aircraft, prevFlightTask, flightTask)
                    && isConnected(graph, nodeMap, flightTask, prevFlightTask)
                ) {
                    insertFlightTask(graph, nodes, nodeMap, node, flightTask)
                }

                if (reverse.contains(flightTask, prevFlightTask)
                    && isConnected(graph, nodeMap, flightTask, prevFlightTask)
                ) {
                    insertFlightTask(graph, nodes, nodeMap, node, flightTask)
                }
            }
            graph.put(node, EndNode)
        }
    }

    private fun searchAndInsertReverseFlightTasks(graph: Graph, nodeMap: Map<FlightTask, Node>, aircraft: Aircraft) {
        val originNodes = graph.nodes.values.toList()
        for (prevNode in originNodes) {
            if (prevNode is RootNode || prevNode is EndNode) {
                continue
            }

            val prevFlightTask = (prevNode as TaskNode).task
            val reverseNodes = ArrayList<TaskNode>()
            val notReverseNodes = ArrayList<TaskNode>()
            for (edge in graph[prevNode]) {
                if (edge.to is EndNode) {
                    continue
                }

                val succFlightTask = (edge.to as TaskNode).task
                if (reverse.contains(prevFlightTask, succFlightTask)) {
                    reverseNodes.add(edge.to)
                }
            }
            for (edge in graph[prevNode]) {
                if (edge.to is EndNode) {
                    continue
                }

                val succFlightTask = (edge.to as TaskNode).task
                if (!reverse.contains(prevFlightTask, succFlightTask)) {
                    var flag = false
                    for (node in reverseNodes) {
                        if (isConnected(graph, nodeMap, succFlightTask, node.task)) {
                            flag = true
                        }
                    }
                    if (!flag) {
                        notReverseNodes.add(edge.to)
                    }
                }
            }

            if (reverseNodes.isEmpty()) {
                continue
            }
            reverseNodes.sortBy { it.time }
            for (i in reverseNodes.indices) {
                val index = UInt64(graph.nodes.size.toULong())
                val depTime = prevFlightTask.time?.begin ?: prevFlightTask.timeWindow!!.begin
                val node = TaskNode(prevFlightTask, maxOf(reverseNodes[i].time + 1.minutes, depTime), index)
                graph.put(node)
                graph.put(reverseNodes[i], node)
                graph.put(node, EndNode)

                for (j in (i + 1) until reverseNodes.size) {
                    graph.put(node, reverseNodes[j])
                }
                for (succNode in notReverseNodes) {
                    graph.put(node, succNode)
                }
            }
        }
    }

    private fun insertFlightTask(
        graph: Graph,
        nodes: MutableList<Pair<Airport, Node>>,
        nodeMap: MutableMap<FlightTask, Node>,
        prevNode: Node,
        flightTask: FlightTask
    ) {
        if (!nodeMap.containsKey(flightTask)) {
            val index = UInt64(graph.nodes.size.toULong())
            assert((flightTask.time != null) xor (flightTask.timeWindow != null))
            val depTime = flightTask.time?.begin ?: flightTask.timeWindow!!.begin
            val node = TaskNode(flightTask, depTime, index)
            graph.put(node)
            graph.put(prevNode, node)
            nodeMap[flightTask] = node
            nodes.add(Pair(flightTask.arr, node))
            for (dep in flightTask.depBackup) {
                nodes.add(Pair(flightTask.actualArr(dep)!!, node))
            }
        } else {
            graph.put(prevNode, nodeMap[flightTask]!!)
        }
    }

    private fun isConnected(
        graph: Graph,
        nodeMap: Map<FlightTask, Node>,
        prevFlightTask: FlightTask,
        succFlightTask: FlightTask
    ): Boolean {
        if (nodeMap.containsKey(prevFlightTask) && nodeMap.containsKey(succFlightTask)) {
            val target = nodeMap[succFlightTask]!!
            val nodes = ArrayList<Node>()
            val visitedNode = HashSet<Node>()
            nodes.add(nodeMap[prevFlightTask]!!)

            while (nodes.isNotEmpty()) {
                val node = nodes.first()
                nodes.removeFirst()
                visitedNode.add(node)

                for (edge in graph[node]) {
                    if (edge.to == target) {
                        return true
                    }
                    if (!visitedNode.contains(edge.to)) {
                        nodes.add(edge.to)
                    }
                }
            }
        }
        return false
    }
}
