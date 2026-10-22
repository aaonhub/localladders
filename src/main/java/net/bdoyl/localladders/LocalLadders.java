package net.bdoyl.localladders;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The Local Ladders: no blocks or items of its own. Server and client: right-click a ladder with a ladder to extend
 * its column ({@link LadderExtender}). Client only: ladders are drawn as 3D rails and rungs
 * ({@link net.bdoyl.localladders.client.Ladder3DModels}), so removing the mod leaves every ladder as it was.
 */
@Mod(LocalLadders.MODID)
public class LocalLadders {
    public static final String MODID = "localladders";

    public LocalLadders() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(LadderExtender.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.bdoyl.localladders.client.Ladder3DModels.init(modBus));
        // Self-test for throwaway copies of a modpack server: see SelfTest. Never set on the live server.
        if (Boolean.getBoolean("localladders.selftest")) MinecraftForge.EVENT_BUS.register(SelfTest.class);
    }
}
