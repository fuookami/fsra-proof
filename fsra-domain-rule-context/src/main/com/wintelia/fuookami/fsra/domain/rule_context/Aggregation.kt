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

    init {
        val restrictions = ArrayList<Restriction>()
        restrictions.addAll(relationRestrictions)
        restrictions.addAll(generalRestrictions)
        this.restrictions = restrictions
    }
}
