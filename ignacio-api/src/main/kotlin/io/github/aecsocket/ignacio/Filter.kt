package io.github.aecsocket.ignacio

data class Filter<T>(
    val all: Set<T>,
    val one: Set<T>,
    val none: Set<T>,
)
