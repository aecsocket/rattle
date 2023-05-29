package io.github.aecsocket.rattle.fabric.mixin;

import io.github.aecsocket.rattle.fabric.FabricRattlePlayer;
import io.github.aecsocket.rattle.fabric.RattlePlayerAccess;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.CallbackI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements RattlePlayerAccess {
    @Unique
    private FabricRattlePlayer data;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(CallbackInfo ci) {
        data = new FabricRattlePlayer((ServerPlayer) (Object) this);
    }

    @Override
    public @NotNull FabricRattlePlayer rattle_getData() {
        return data;
    }
}
