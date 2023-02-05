package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Transform

interface PhysicsBody : Destroyable {
    var transform: Transform
}

interface RigidBody : PhysicsBody

interface StaticBody : RigidBody

interface DynamicBody : RigidBody
