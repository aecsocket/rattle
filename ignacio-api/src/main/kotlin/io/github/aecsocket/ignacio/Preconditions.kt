package io.github.aecsocket.ignacio

fun assertProp(condition: Boolean, prop: String, message: String) {
    if (!condition)
        throw IllegalArgumentException("($prop) assertion failed: $message")
}

fun <T : Comparable<T>> assertGt(prop: String, expected: T, value: T) =
    assertProp(value > expected, prop, "$value > $expected")

fun <T : Comparable<T>> assertGtEq(prop: String, expected: T, value: T) =
    assertProp(value >= expected, prop, "$value >= $expected")

fun <T : Comparable<T>> assertLt(prop: String, expected: T, value: T) =
    assertProp(value < expected, prop, "$value < $expected")

fun <T : Comparable<T>> assertLtEq(prop: String, expected: T, value: T) =
    assertProp(value <= expected, prop, "$value <= $expected")
