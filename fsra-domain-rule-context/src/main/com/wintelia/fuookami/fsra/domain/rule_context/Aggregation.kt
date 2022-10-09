package com.wintelia.fuookami.fsra.domain.rule_context

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class Aggregation(
    val enabledAircrafts: Set<Aircraft>,
    val flowControls: List<FlowControl>,
    val relationRestrictions: List<RelationRestriction>,
    val linkMap: FlightLinkMap
) {
    val restriction: List<Restriction>

    init {
        val restriction = ArrayList<Restriction>()
        restriction.addAll(relationRestrictions)
        this.restriction = restriction
    }
}
