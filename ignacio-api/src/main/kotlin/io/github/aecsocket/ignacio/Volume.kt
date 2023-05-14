package io.github.aecsocket.ignacio

sealed interface Volume<VR : VolumeAccess, VW : VR> {
    data class Single(
        val collider: Collider,
    ) : Volume<VolumeAccess.Single, VolumeAccess.Single.Write>

    data class Compound(
        val colliders: Collection<Collider>,
    ) : Volume<VolumeAccess.Compound, VolumeAccess.Compound.Write>
}

interface CompoundChild

interface VolumeAccess {
    interface Single : VolumeAccess {
        operator fun invoke(): Collider

        interface Write : Single {
            operator fun invoke(collider: Collider)
        }
    }

    interface Compound : VolumeAccess {
        // todo iterate
        interface Write : Compound {
            fun attach(collider: Collider): CompoundChild

            fun detach(child: CompoundChild)
        }
    }
}
