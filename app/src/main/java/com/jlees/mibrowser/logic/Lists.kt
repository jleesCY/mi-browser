// List helpers shared by features.
package com.jlees.mibrowser.logic

/** [list] with the item at [from] moved to [to] (a list rearranged by dragging); unchanged if either is out of range. */
fun <T> moved(list: List<T>, from: Int, to: Int): List<T> =
    if (from !in list.indices || to !in list.indices) list else list.toMutableList().apply { add(to, removeAt(from)) }
