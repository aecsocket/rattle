package io.github.aecsocket.rattle.fabric.mixin;

import io.github.aecsocket.rattle.fabric.FabricRattlePlayer;
import io.github.aecsocket.rattle.fabric.FabricRattle;
import io.github.aecsocket.rattle.fabric.PlayerRattleAccess;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements PlayerRattleAccess {
    @Unique
    private FabricRattlePlayer rattle;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(CallbackInfo ci) {
        rattle = new FabricRattlePlayer(FabricRattle.api(), (ServerPlayer) (Object) this);
    }

    @Override
    public @NotNull FabricRattlePlayer rattle() {
        return rattle;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick(CallbackInfo ci) {
        rattle.tick();
    }
}
