package net.bdoyl.localladders;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Local Ladders: no blocks or items of its own. Server and client: right-click a ladder with a ladder to extend
 * its column ({@link LadderExtender}). Client only: ladders are drawn as 3D rails and rungs
 * ({@link net.bdoyl.localladders.client.LocalLaddersClient}), so removing the mod leaves every ladder as it was.
 */
@Mod(LocalLadders.MODID)
public class LocalLadders {
    public static final String MODID = "localladders";

    public LocalLadders() {
        NeoForge.EVENT_BUS.register(LadderExtender.class);
        // Self-test for throwaway copies of a modpack server: see SelfTest. Never set on the live server.
        if (Boolean.getBoolean("localladders.selftest")) NeoForge.EVENT_BUS.register(SelfTest.class);
    }
}
