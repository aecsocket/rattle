package io.github.aecsocket.rattle.fabric.mixin;

import io.github.aecsocket.rattle.fabric.FabricRattlePlayer;
import io.github.aecsocket.rattle.fabric.RattlePlayerAccess;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements RattlePlayerAccess {
    @Unique
    private final FabricRattlePlayer data = new FabricRattlePlayer((ServerPlayer) (Object) this);

    @Override
    public @NotNull FabricRattlePlayer rattle_getData() {
        return data;
    }
}
