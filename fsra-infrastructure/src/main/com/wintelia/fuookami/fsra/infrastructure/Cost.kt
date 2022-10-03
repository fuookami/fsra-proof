package com.wintelia.fuookami.fsra.infrastructure

import fuookami.ospf.kotlin.utils.math.*

data class CostItem(
    val tag: String,
    val value: Flt64? = null,
    val message: String? = null
) {
    val valid get() = value != null
}

data class Cost(
    private val _items: MutableList<CostItem> = ArrayList(),
    var sum: Flt64? = Flt64.zero
) : Iterable<CostItem> {
    val items: List<CostItem> get() = _items
    val valid: Boolean get() = sum != null

    operator fun plusAssign(rhs: CostItem) {
        _items.add(rhs)

        if (rhs.valid) {
            sum = sum?.plus(rhs.value!!)
        }
    }

    override fun iterator() = _items.iterator()
}
