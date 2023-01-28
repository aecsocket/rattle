package io.gitlab.aecsocket.ignacio.core.command

import cloud.commandframework.CommandManager
import cloud.commandframework.arguments.compound.ArgumentTriplet
import cloud.commandframework.types.tuples.Triplet
import io.gitlab.aecsocket.ignacio.core.math.Vec3

inline fun <X, reified A, reified B, reified C, reified R : Any> CommandManager<X>.argumentTriplet(
    name: String,
    argA: String,
    argB: String,
    argC: String,
    crossinline mapper: (X, A, B, C) -> R
) = ArgumentTriplet.of(
    this, name,
    Triplet.of(argA, argB, argC),
    Triplet.of(A::class.java, B::class.java, C::class.java)
).withMapper(R::class.java) { ctx, triplet -> mapper(ctx, triplet.first, triplet.second, triplet.third) }

fun <C> CommandManager<C>.argumentVec3(name: String) =
    argumentTriplet<C, Double, Double, Double, Vec3>(
        name,"x", "y", "z"
    ) { _, x, y, z -> Vec3(x, y, z) }
