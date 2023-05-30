package io.github.aecsocket.rattle.fabric.mixin;

import io.github.aecsocket.rattle.fabric.RattleMod;
import io.github.aecsocket.rattle.fabric.RattleServerAccess;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ServerMixin implements RattleServerAccess {
    @Unique
    private RattleMod.Server data;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(CallbackInfo ci) {
        data = new RattleMod.Server(RattleMod.api(), (MinecraftServer) (Object) this);
    }

    @Override
    public @NotNull RattleMod.Server rattle_getData() {
        return data;
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        data.onTick();
    }
}
