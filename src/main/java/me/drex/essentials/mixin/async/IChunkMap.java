package me.drex.essentials.mixin.async;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface IChunkMap {

    @Invoker
    ChunkHolder invokeGetUpdatingChunkIfPresent(long pos);

}
