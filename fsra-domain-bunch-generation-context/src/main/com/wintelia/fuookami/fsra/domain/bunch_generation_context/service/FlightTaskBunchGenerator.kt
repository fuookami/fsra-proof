package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import kotlin.time.*
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

private data class LabelBuilder(
    var cost: Cost = Cost(),
    var shadowPrice: Flt64 = Flt64.zero,
    val delay: Duration = Duration.ZERO,
    val arrivalTime: Instant,
    val flightHour: FlightHour = FlightHour.zero,
    val flightCycle: FlightCycle = FlightCycle.zero,

    val prevLabel: Label? = null,
    val node: Node,
    val flightTask: FlightTask? = null
) {
    companion object {
        operator fun invoke(node: Node, arrivalTime: Instant) = LabelBuilder(
            arrivalTime = arrivalTime,
            node = node
        )

        operator fun invoke(node: Node, previousLabel: Label) = LabelBuilder(
            arrivalTime = previousLabel.arrivalTime,
            node = node,
            prevLabel = previousLabel
        )

        operator fun invoke(node: Node, previousLabel: Label, recoveryFlightTask: FlightTask) = LabelBuilder(
            delay = previousLabel.delay + recoveryFlightTask.delay,
            arrivalTime = recoveryFlightTask.time!!.end,
            flightHour = previousLabel.flightHour + recoveryFlightTask.flightHour!!,
            flightCycle = previousLabel.flightCycle + recoveryFlightTask.flightCycle,
            prevLabel = previousLabel,
            node = node,
            flightTask = recoveryFlightTask
        )
    }
}

private data class Label(
    val cost: Cost,
    val shadowPrice: Flt64,
    val delay: Duration,
    val arrivalTime: Instant,
    val flightHour: FlightHour,
    val flightCycle: FlightCycle,

    val prevLabel: Label?,
    val node: Node,
    val flightTask: FlightTask?
) {
    companion object {
        operator fun invoke(builder: LabelBuilder) = Label(
            cost = builder.cost,
            shadowPrice = builder.shadowPrice,
            delay = builder.delay,
            arrivalTime = builder.arrivalTime,
            flightHour = builder.flightHour,
            flightCycle = builder.flightCycle,
            prevLabel = builder.prevLabel,
            node = builder.node,
            flightTask = builder.flightTask
        )
    }

    val reducedCost get() = cost.sum!! - shadowPrice
    val aircraftChange: UInt64 = (prevLabel?.aircraftChange ?: UInt64.zero) +
            if (flightTask?.aircraftChanged == true) { UInt64.one } else { UInt64.zero }
    val trace: List<UInt64>
    val isBetterBunch get() = reducedCost leq Flt64.zero
    val originFlightTask get() = flightTask?.originTask

    init {
        assert(when(node){
            is TaskNode -> { flightTask != null }
            is RootNode -> { flightTask == null && prevLabel == null }
            is EndNode -> { flightTask == null}
        })

        val trace = prevLabel?.trace?.toMutableList() ?: ArrayList()
        when (node) {
            is TaskNode -> { trace.add(node.index) }
            else -> { }
        }
        this.trace = trace
    }

    fun visited(node: Node): Boolean {
        return when (node) {
            is RootNode, is EndNode -> { false }
            is TaskNode -> { return trace.contains(node.index) }
        }
    }

    fun generateBunch(iteration: UInt64, aircraft: Aircraft, aircraftUsability: AircraftUsability, totalCostCalculator: TotalCostCalculator): FlightTaskBunch? {
        assert(node is EndNode)
        // in beginning, it should be the succ node of root node at the top of the stack
        // it means that nodes in the stack is in descending order
        // so the flights will be in increasing order
        val labels = ArrayList<Label>()
        var label = prevLabel
        while (label!!.node !is RootNode) {
            labels.add(label)
            label = label.prevLabel
        }

        val flightTasks = ArrayList<FlightTask>()
        while (labels.isNotEmpty()) {
            label = labels.last()
            labels.removeLast()

            flightTasks.add(label.flightTask!!)
        }
        val totalCost = totalCostCalculator(aircraft, flightTasks)
        return totalCost?.let { FlightTaskBunch(aircraft, aircraftUsability, flightTasks, iteration, it)}
    }

    infix fun ls(rhs: Label): Boolean {
        return reducedCost ls rhs.reducedCost
                && delay <= rhs.delay
                && ((node is EndNode) || (aircraftChange > rhs.aircraftChange))
                // && flightHour leq rhs.flightHour
                // && flightCycle leq rhs.flightCycle
    }
}

typealias ConnectionTimeCalculator = (Aircraft, FlightTask, FlightTask?) -> Duration
typealias MinimumDepartureTimeCalculator = (Instant, Aircraft, FlightTask, Duration) -> Instant
typealias CostCalculator = (Aircraft, FlightTask?, FlightTask, FlightHour, FlightCycle) -> Cost?
typealias TotalCostCalculator = (Aircraft, List<FlightTask>) -> Cost?
private typealias LabelMap = MutableMap<Node, MutableList<Label>>

class FlightTaskBunchGenerator(
    val aircraft: Aircraft,
    val aircraftUsability: AircraftUsability,
    val graph: Graph,
    val connectionTimeCalculator: ConnectionTimeCalculator,
    val minimumDepartureTimeCalculator: MinimumDepartureTimeCalculator,
    val costCalculator: CostCalculator,
    val totalCostCalculator: TotalCostCalculator
) {
    companion object {
        const val maxAmount = 60

        private fun sortNodes(graph: Graph): List<Node> {
            val inDegree = HashMap<Node, UInt64>()
            for ((_, node) in graph.nodes) {
                inDegree[node] = UInt64.zero
            }
            for ((_, node) in graph.nodes) {
                for (edge in graph[node]) {
                    inDegree[edge.to] = (inDegree[edge.to]?: UInt64.zero) + UInt64.one
                }
            }

            // topological sort
            val nodes = ArrayList<Node>()
            while (inDegree.isNotEmpty()) {
                val removeNeededNodes = inDegree.filterValues { it == UInt64.zero }
                if (removeNeededNodes.isNotEmpty()) {
                    for ((node, _) in removeNeededNodes) {
                        for (edge in graph[node]) {
                            inDegree[edge.to] = inDegree[edge.to]!! - UInt64.one
                        }
                        nodes.add(node)
                        inDegree.remove(node)
                    }
                } else {
                    val minInDegree = inDegree.values.min()
                    val minInDegreeNodes = inDegree.filterValues { it == minInDegree }
                    nodes.addAll(minInDegreeNodes.keys.toList().sortedBy { it.time })
                    for ((node, _) in minInDegreeNodes) {
                        for (edge in graph[node]) {
                            inDegree[edge.to] = inDegree[edge.to]!! - UInt64.one
                        }
                        inDegree.remove(node)
                    }
                }
            }
            return nodes
        }
    }

    private val enabledTime by aircraftUsability::enabledTime
    private val nodes = sortNodes(graph)

    operator fun invoke(iteration: UInt64, shadowPriceMap: ShadowPriceMap): List<FlightTaskBunch> {
        val labels: LabelMap = HashMap()
        for (prevNode in nodes) {
            for (prevLabel in getLabels(labels, prevNode)) {
                for (edge in graph[prevNode]) {
                    val succNode = edge.to
                    val succLabels = getLabels(labels, succNode)

                    if (succNode is EndNode) {
                        if (prevNode !is RootNode) {
                            val builder = LabelBuilder(succNode, prevLabel)
                            builder.shadowPrice += shadowPriceMap(prevLabel.flightTask, null, aircraft)
                            insertLabel(succLabels, Label(builder), false)
                        }
                    } else if (!prevLabel.visited(succNode)) {
                        val succLabel = generateFlightTaskLabel(prevLabel, succNode, shadowPriceMap)
                        if (succLabel != null) {
                            insertLabel(succLabels, succLabel)
                        }
                    }
                }
            }
        }
        return selectBunches(iteration, labels[EndNode]!!)
    }

    private fun initRootLabel(labels: LabelMap, shadowPriceMap: ShadowPriceMap) {
        assert(labels.isEmpty())
        val rootNode = graph[Node.root]!!
        val builder = LabelBuilder(rootNode, enabledTime)
        builder.shadowPrice += shadowPriceMap(null, null, aircraft)
        labels[rootNode] = mutableListOf(Label(builder))
    }

    private fun getLabels(labels: LabelMap, node: Node): MutableList<Label> {
        if (!labels.containsKey(node)) {
            labels[node] = ArrayList()
        }
        return labels[node]!!
    }

    private fun generateFlightTaskLabel(prevLabel: Label, succNode: Node, shadowPriceMap: ShadowPriceMap): Label? {
        assert(succNode is TaskNode)
        if (prevLabel.node !is RootNode && prevLabel.prevLabel!!.node !is RootNode) {
            if ((succNode as TaskNode).task == prevLabel.prevLabel!!.originFlightTask) {
                return null
            }
        }

        val succTask = (succNode as TaskNode).task
        assert((succTask.scheduledTime != null) or (succTask.timeWindow != null))

        val minDepTime = getMinDepartureTime(prevLabel, succNode)
        val duration = succTask.duration(aircraft)
        val time = if (succTask.scheduledTime != null) {
            TimeRange(minDepTime, minDepTime + duration)
        } else {
            if ((minDepTime + duration) > succTask.timeWindow!!.end) {
                return null
            }
            TimeRange(minDepTime, minDepTime + duration)
        }

        val prevArr = if (prevLabel.node is RootNode) { succTask.dep } else { prevLabel.flightTask!!.arr }
        val recoveryTask = generateRecoveryFlightTask(prevArr, succTask, time) ?: return null

        val flightHour = prevLabel.flightHour + recoveryTask.flightHour!!
        val flightCycle = prevLabel.flightCycle + recoveryTask.flightCycle
        val cost = if (prevLabel.node is RootNode) {
            costCalculator(aircraft, aircraftUsability.lastTask, recoveryTask, flightHour, flightCycle)
        } else {
            costCalculator(aircraft, prevLabel.flightTask!!, recoveryTask, flightHour, flightCycle)
        }
        if (cost == null || !cost.valid) {
            return null
        }
        val shadowPrice = if (prevLabel.node is RootNode) {
            shadowPriceMap(aircraftUsability.lastTask, recoveryTask, aircraft)
        } else {
            shadowPriceMap(prevLabel.flightTask!!, recoveryTask, aircraft)
        }

        val builder = LabelBuilder(succNode, prevLabel, recoveryTask)
        builder.cost += cost
        builder.shadowPrice += shadowPrice
        return Label(builder)
    }

    private fun generateRecoveryFlightTask(dep: Airport, succTask: FlightTask, time: TimeRange): FlightTask? {
        val aircraft: Aircraft? = if (succTask.aircraft == null || succTask.aircraft != this.aircraft) {
            this.aircraft
        } else {
            null
        }

        val arr = succTask.actualArr(dep) ?: return null

        val recoveryPolicy = RecoveryPolicy(
            aircraft = aircraft,
            time = time,
            route = Pair(dep, arr)
        )

        return if (recoveryPolicy.empty) {
            succTask
        } else if (succTask.recoveryEnabled(recoveryPolicy)) {
            null
        } else {
            succTask.recovery(recoveryPolicy)
        }
    }

    private fun insertLabel(labels: MutableList<Label>, label: Label, withDeletion: Boolean = true) {
        if (withDeletion) {
            if (labels.any { it ls label }) {
                return
            }
            labels.removeIf { label ls it }
        }

        for (i in labels.indices) {
            if (label.reducedCost ls labels[i].reducedCost) {
                labels.add(i, label)
                return
            }
        }
        // add to tail
        labels.add(label)
    }

    // extract flight bunches of the best labels in end node
    private fun selectBunches(iteration: UInt64, labels: List<Label>): List<FlightTaskBunch> {
        val bunches = ArrayList<FlightTaskBunch>()
        for (label in labels.asIterable().filter { it.isBetterBunch }) {
            val newBunch = label.generateBunch(iteration, aircraft, aircraftUsability, totalCostCalculator);
            if (newBunch != null) {
                bunches.add(newBunch)
            }
            if (bunches.size == maxAmount) {
                break
            }
        }
        return bunches
    }

    // calculate the minimum departure time from previous label
    // specially, it is the arrival time of last label if the previous label is belongs to root node or succ node is end node
    // because there is no connection between virtual node and flight node
    private fun getMinDepartureTime(prevLabel: Label, succNode: Node): Instant {
        assert(succNode is TaskNode)
        return if (prevLabel.node is RootNode && aircraftUsability.lastTask  == null) {
            aircraftUsability.enabledTime
        } else {
            val prevFlightTask = prevLabel.flightTask!!
            val thisFlightTask = (succNode as TaskNode).task
            val connectionTime = connectionTimeCalculator(aircraft, prevFlightTask, thisFlightTask)
            minimumDepartureTimeCalculator(prevLabel.arrivalTime, aircraft, thisFlightTask, connectionTime)
        }
    }
}
