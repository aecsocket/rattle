package io.github.aecsocket.rattle.fabric.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.class)
public interface DisplayAccess {
  @Accessor("DATA_LEFT_ROTATION_ID")
  static EntityDataAccessor<Quaternionf> getDataLeftRotationId() {
    throw new AssertionError();
  }
}
