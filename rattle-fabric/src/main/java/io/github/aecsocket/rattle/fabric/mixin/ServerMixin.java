package io.github.aecsocket.rattle.fabric.mixin;

import io.github.aecsocket.rattle.fabric.FabricRattle;
import io.github.aecsocket.rattle.fabric.FabricRattlePlatform;
import io.github.aecsocket.rattle.fabric.ServerRattleAccess;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ServerMixin implements ServerRattleAccess {
    @Unique
    private FabricRattlePlatform rattle;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(CallbackInfo ci) {
        rattle = new FabricRattlePlatform(FabricRattle.api(), (MinecraftServer) (Object) this);
    }

    @Override
    public @NotNull FabricRattlePlatform rattle() {
        return rattle;
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        rattle.onTick();
    }
}
