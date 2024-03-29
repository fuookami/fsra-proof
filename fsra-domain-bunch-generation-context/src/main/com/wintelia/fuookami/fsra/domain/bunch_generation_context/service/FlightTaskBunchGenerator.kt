package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.model.*

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
            cost = previousLabel.cost.copy(),
            shadowPrice = previousLabel.shadowPrice,
            arrivalTime = previousLabel.arrivalTime,
            node = node,
            prevLabel = previousLabel
        )

        operator fun invoke(node: Node, previousLabel: Label, recoveryFlightTask: FlightTask) = LabelBuilder(
            cost = previousLabel.cost.copy(),
            shadowPrice = previousLabel.shadowPrice,
            delay = previousLabel.delay + recoveryFlightTask.delay,
            arrivalTime = recoveryFlightTask.time!!.end,
            flightHour = previousLabel.flightHour + (recoveryFlightTask.flightHour ?: FlightHour.zero),
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
    val aircraftChange: UInt64 = // (prevLabel?.aircraftChange ?: UInt64.zero) +
        if (flightTask?.aircraftChanged == true) {
            UInt64.one
        } else {
            UInt64.zero
        }
    val trace: List<UInt64>
    val isBetterBunch get() = ls(reducedCost, Flt64.zero)
    val originFlightTask get() = flightTask?.originTask

    init {
        assert(
            when (node) {
                is TaskNode -> {
                    flightTask != null
                }

                is RootNode -> {
                    flightTask == null && prevLabel == null
                }

                is EndNode -> {
                    flightTask == null
                }
            }
        )

        val trace = prevLabel?.trace?.toMutableList() ?: ArrayList()
        when (node) {
            is TaskNode -> {
                trace.add(node.index)
            }

            else -> {}
        }
        this.trace = trace
    }

    fun visited(node: Node): Boolean {
        return when (node) {
            is RootNode, is EndNode -> {
                false
            }

            is TaskNode -> {
                return trace.contains(node.index)
            }
        }
    }

    fun generateBunch(
        iteration: UInt64,
        aircraft: Aircraft,
        aircraftUsability: AircraftUsability,
        totalCostCalculator: TotalCostCalculator
    ): FlightTaskBunch? {
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
        return totalCost?.let { FlightTaskBunch(aircraft, aircraftUsability, flightTasks, iteration, it) }
    }

    infix fun ls(rhs: Label): Boolean {
        return ls(reducedCost, rhs.reducedCost)                                     // leq for faster
                && delay <= rhs.delay
                && ((node is EndNode) || (aircraftChange >= rhs.aircraftChange))
        // && flightHour leq rhs.flightHour
        // && flightCycle leq rhs.flightCycle
    }
}

private typealias LabelMap = MutableMap<Node, MutableList<Label>>

class FlightTaskBunchGenerator(
    private val aircraft: Aircraft,
    private val aircraftUsability: AircraftUsability,
    private val graph: Graph,
    private val connectionTimeCalculator: ConnectionTimeCalculator,
    private val minimumDepartureTimeCalculator: MinimumDepartureTimeCalculator,
    private val costCalculator: CostCalculator,
    private val totalCostCalculator: TotalCostCalculator,
    private val configuration: Configuration
) {
    companion object {
        private fun sortNodes(graph: Graph): List<Node> {
            val inDegree = HashMap<Node, UInt64>()
            for ((_, node) in graph.nodes) {
                inDegree[node] = UInt64.zero
            }
            for ((_, node) in graph.nodes) {
                for (edge in graph[node]) {
                    inDegree[edge.to] = (inDegree[edge.to] ?: UInt64.zero) + UInt64.one
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
    private val nodes = if (!configuration.withOrderChange) { sortNodes(graph) } else { emptyList() }

    operator fun invoke(iteration: UInt64, shadowPriceMap: ShadowPriceMap): Result<List<FlightTaskBunch>, Error> {
        val labels: LabelMap = HashMap()
        initRootLabel(labels, shadowPriceMap)

        if (configuration.withOrderChange) {
            val labelDeque = ArrayList<Label>()
            labelDeque.addAll(labels[graph[Node.root]!!]!!)

            while (labelDeque.isNotEmpty()) {
                val prevLabel = labelDeque.first()
                labelDeque.removeFirst()
                val prevNode = prevLabel.node
                val edges = graph[prevNode].sortedBy { it.to.time }

                for (edge in edges) {
                    val succNode = edge.to
                    val succLabels = getLabels(labels, succNode)
                    if (succNode is EndNode) {
                        if (prevNode !is RootNode) {
                            val builder = LabelBuilder(succNode, prevLabel)
                            builder.shadowPrice += shadowPriceMap(prevLabel.flightTask, null, aircraft)
                            insertLabel(succLabels, Label(builder))
                        }
                    } else if (!prevLabel.visited(succNode)) {
                        val succLabel = generateFlightTaskLabel(prevLabel, succNode, shadowPriceMap)
                        if (succLabel != null) {
                            insertLabel(succLabels, succLabel)
                            labelDeque.add(succLabel)
                        }
                    }
                }
            }
        }
        else {
            for (prevNode in nodes) {
                for (prevLabel in getLabels(labels, prevNode)) {
                    for (edge in graph[prevNode]) {
                        val succNode = edge.to
                        val succLabels = getLabels(labels, succNode)

                        if (succNode is EndNode) {
                            if (prevNode !is RootNode) {
                                val builder = LabelBuilder(succNode, prevLabel)
                                builder.shadowPrice += shadowPriceMap(prevLabel.flightTask, null, aircraft)
                                insertLabel(succLabels, Label(builder))
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
        }
        return Ok(selectBunches(iteration, labels[EndNode]!!))
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
        succNode as TaskNode

        val succTask = succNode.task
        assert((succTask.scheduledTime != null) xor (succTask.timeWindow != null))

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

        val prevArr = if (prevLabel.node is RootNode) {
            succTask.dep
        } else {
            prevLabel.flightTask!!.arr
        }
        val recoveryTask = generateRecoveryFlightTask(prevArr, succTask, time) ?: return null

        val flightHour = prevLabel.flightHour + (recoveryTask.flightHour ?: FlightHour.zero)
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
            route = Route(dep, arr)
        )

        return if (recoveryPolicy.empty) {
            succTask
        } else if (!succTask.recoveryEnabled(recoveryPolicy)) {
            null
        } else {
            succTask.recovery(recoveryPolicy)
        }
    }

    private fun insertLabel(labels: MutableList<Label>, label: Label) {
        when (label.node) {
            is TaskNode -> {
                if (labels.any { it ls label }) {
                    return
                }
                labels.removeAll { label ls it }
                if (labels.size > configuration.maximumLabelPerNode.toInt()) {
                    for (i in configuration.maximumLabelPerNode.toInt() until labels.size) {
                        labels.removeAt(i)
                    }
                }

                for (i in labels.indices) {
                    if (ls(label.reducedCost, labels[i].reducedCost)) {
                        labels.add(i, label)
                        return
                    }
                }
                // add to tail
                labels.add(label)
            }
            is EndNode -> {
                labels.add(label)
            }
            else -> { }
        }
    }

    // extract flight bunches of the best labels in end node
    private fun selectBunches(iteration: UInt64, labels: List<Label>): List<FlightTaskBunch> {
        val bunches = ArrayList<FlightTaskBunch>()
        val sortedLabels = labels.asIterable().filter { it.isBetterBunch }.sortedBy { it.reducedCost }
        for (label in sortedLabels) {
            val newBunch = label.generateBunch(iteration, aircraft, aircraftUsability, totalCostCalculator);
            if (newBunch != null) {
                bunches.add(newBunch)
            }
            if (bunches.size == configuration.maximumColumnGeneratedPerAircraft.toInt()) {
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
        val thisFlightTask = (succNode as TaskNode).task
        return if (prevLabel.node is RootNode && aircraftUsability.lastTask == null) {
            minimumDepartureTimeCalculator(prevLabel.arrivalTime, aircraft, thisFlightTask, 0.minutes)
        } else {
            val prevFlightTask = if (prevLabel.node is RootNode) {
                aircraftUsability.lastTask!!
            } else {
                prevLabel.flightTask!!
            }
            val connectionTime = connectionTimeCalculator(aircraft, prevFlightTask, thisFlightTask)
            minimumDepartureTimeCalculator(prevLabel.arrivalTime, aircraft, thisFlightTask, connectionTime)
        }
    }
}
