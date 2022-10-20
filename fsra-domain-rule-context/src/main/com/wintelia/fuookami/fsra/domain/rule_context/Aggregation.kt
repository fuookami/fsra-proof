package com.wintelia.fuookami.fsra.domain.rule_context

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class Aggregation(
    val enabledAircrafts: Set<Aircraft>,
    val flowControls: List<FlowControl>,
    val relationRestrictions: List<RelationRestriction>,
    val generalRestrictions: List<GeneralRestriction>,
    val linkMap: FlightLinkMap,
    val lock: Lock
) {
    val restrictions: List<Restriction>
    private val restrictionMap: Map<Aircraft, List<Restriction>>
    val flightTasks: List<FlightTask>

    init {
        val restrictions = ArrayList<Restriction>()
        restrictions.addAll(relationRestrictions)
        restrictions.addAll(generalRestrictions)
        this.restrictions = restrictions

        val restrictionMap = HashMap<Aircraft, List<Restriction>>()
        for (aircraft in enabledAircrafts) {
            restrictionMap[aircraft] = restrictions.filter { it.related(aircraft) }
        }
        this.restrictionMap = restrictionMap

        this.flightTasks = ArrayList()
    }

    fun restrictions(aircraft: Aircraft): List<Restriction> {
        return restrictionMap[aircraft] ?: emptyList()
    }

    fun enabled(aircraft: Aircraft): Boolean {
        return enabledAircrafts.contains(aircraft)
    }
}
