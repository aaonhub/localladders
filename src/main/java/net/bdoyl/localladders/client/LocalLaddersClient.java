package net.bdoyl.localladders.client;

import net.bdoyl.localladders.LocalLadders;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** The client-only half of the mod: the 3D ladder models ({@link Ladder3DModels}). */
@Mod(value = LocalLadders.MODID, dist = Dist.CLIENT)
public final class LocalLaddersClient {
    public LocalLaddersClient(IEventBus modBus) {
        Ladder3DModels.init(modBus);
    }
}
