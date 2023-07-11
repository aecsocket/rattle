package io.github.aecsocket.rattle.fabric.mixin;

import io.github.aecsocket.kbeam.sync.Locked;
import io.github.aecsocket.rattle.fabric.FabricWorldPhysics;
import io.github.aecsocket.rattle.fabric.LevelPhysicsAccess;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements LevelPhysicsAccess {
  @Unique private @Nullable Locked<FabricWorldPhysics> physics;

  @Override
  public @Nullable Locked<FabricWorldPhysics> rattle_getPhysics() {
    return physics;
  }

  @Override
  public void rattle_setPhysics(@Nullable Locked<FabricWorldPhysics> physics) {
    this.physics = physics;
  }
}
