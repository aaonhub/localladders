package net.bdoyl.localladders;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A 9x16x9 test space for {@link LadderScenarios}: a GameTest in dev, a forceloaded patch of sky in {@link SelfTest}. */
interface TestArena {
    int SIZE_X = 9, SIZE_Y = 16, SIZE_Z = 9;

    ServerLevel level();

    BlockPos abs(BlockPos rel);

    /** Runs {@code r} after {@code ticks} server ticks (a failed check inside it fails the scenario). */
    void later(int ticks, Runnable r);

    /** Throws (ending the scenario as failed) unless {@code ok}. */
    void check(boolean ok, String message);

    void pass();

    default void setBlock(BlockPos rel, Block block) {
        setBlock(rel, block.defaultBlockState());
    }

    default void setBlock(BlockPos rel, BlockState state) {
        level().setBlock(abs(rel), state, Block.UPDATE_ALL);
    }

    default BlockState getBlockState(BlockPos rel) {
        return level().getBlockState(abs(rel));
    }
}
