package io.github.aecsocket.rattle.fabric.mixin;

import io.github.aecsocket.rattle.fabric.RattleInternalKt;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(MinecraftServer.class)
public abstract class ServerMixin {
    @Unique
    private final AtomicBoolean stepping = new AtomicBoolean(false);

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (stepping.getAndSet(true)) return;

        RattleInternalKt.stepServer((MinecraftServer) (Object) this);

        stepping.set(false);
    }
}
