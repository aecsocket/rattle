package io.github.aecsocket.rattle.fabric.mixin;

import io.github.aecsocket.rattle.fabric.FabricRattlePlayer;
import io.github.aecsocket.rattle.fabric.Rattle;
import io.github.aecsocket.rattle.fabric.RattleServerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ServerMixin implements RattleServerAccess {
    @Unique
    private Rattle.Server data;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(CallbackInfo ci) {
        data = new Rattle.Server((MinecraftServer) (Object) this);
    }

    @Override
    public @NotNull Rattle.Server rattle_getData() {
        return data;
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        data.onTick();
    }
}
