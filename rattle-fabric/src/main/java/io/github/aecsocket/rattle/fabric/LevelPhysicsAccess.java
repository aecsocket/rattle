package io.github.aecsocket.rattle.fabric;

import io.github.aecsocket.alexandria.sync.Locked;
import org.jetbrains.annotations.Nullable;

public interface LevelPhysicsAccess {
    @Nullable Locked<FabricWorldPhysics> rattle_getPhysics();

    void rattle_setPhysics(@Nullable Locked<FabricWorldPhysics> physics);
}
