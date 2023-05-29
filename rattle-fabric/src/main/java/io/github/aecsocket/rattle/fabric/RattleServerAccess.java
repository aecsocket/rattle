package io.github.aecsocket.rattle.fabric;

import org.jetbrains.annotations.NotNull;

public interface RattleServerAccess {
    @NotNull Rattle.Server rattle_getData();
}
