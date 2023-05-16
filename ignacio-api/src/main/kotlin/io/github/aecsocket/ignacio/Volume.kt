package io.github.aecsocket.ignacio

// This is effectively an algebraic data type, and the generics here
// are only used to allow inferring the type of VolumeAccess that the Volume provides.
// However, when matching over different Volume types, we can't actually reify these type parameters.
// Therefore, the type system tends to scream at us unless we explicitly suppress cast warnings.
// See PhysicsSpace implementations for an example.
sealed interface Volume<VR : VolumeAccess, VW : VR> {
    data class Fixed(
        val colliders: Collection<Collider>,
    ) : Volume<VolumeAccess.Fixed, VolumeAccess.Fixed>

    data class Mutable(
        val collider: Collider,
    ) : Volume<VolumeAccess.Mutable, VolumeAccess.Mutable.Write>

    data class Compound(
        val colliders: Collection<Collider>,
    ) : Volume<VolumeAccess.Compound, VolumeAccess.Compound.Write>
}

interface CompoundChild

interface VolumeAccess {
    object Fixed : VolumeAccess

    interface Mutable : VolumeAccess {
        operator fun invoke(): Collider

        interface Write : Mutable {

            operator fun invoke(collider: Collider)
        }
    }

    interface Compound : VolumeAccess, Iterable<CompoundChild> {
        interface Write : Compound {
            fun attach(collider: Collider): CompoundChild

            fun detach(child: CompoundChild)
        }
    }
}
