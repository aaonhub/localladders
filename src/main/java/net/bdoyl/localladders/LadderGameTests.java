package net.bdoyl.localladders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** {@link LadderScenarios} as GameTests (./gradlew runGameTestServer). NeoForge only registers these in a dev workspace. */
@GameTestHolder(LocalLadders.MODID)
public class LadderGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> scenarios() {
        List<TestFunction> tests = new ArrayList<>();
        LadderScenarios.ALL.forEach((name, s) -> tests.add(new TestFunction("localladders", "localladders." + name,
                LocalLadders.MODID + ":arena", s.maxTicks(), 0L, true, h -> s.run().accept(arena(h)))));
        return tests;
    }

    private static TestArena arena(GameTestHelper h) {
        return new TestArena() {
            @Override public ServerLevel level() { return h.getLevel(); }
            @Override public BlockPos abs(BlockPos rel) { return h.absolutePos(rel); }
            @Override public void later(int ticks, Runnable r) { h.runAfterDelay(ticks, r); }
            @Override public void check(boolean ok, String message) { h.assertTrue(ok, message); }
            @Override public void pass() { h.succeed(); }
        };
    }
}
