package io.github.aecsocket.rattle.fabric;

import org.jetbrains.annotations.Nullable;

public interface LevelPhysicsAccess {
    @Nullable FabricWorldPhysics rattle_getPhysics();

    void rattle_setPhysics(@Nullable FabricWorldPhysics physics);
}
