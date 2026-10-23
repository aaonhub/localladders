package net.bdoyl.localladders;

import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A stone wall with a column of ladders on it, a fake player in front holding ladders who right-clicks one through the
 * real server path ({@code ServerPlayerGameMode.useItemOn}, which fires the same event a real click does). Each
 * scenario runs in a {@link TestArena}: as GameTests in dev ({@link LadderGameTests}, ./gradlew runGameTestServer) or
 * inside a full modpack server with -Dlocalladders.selftest=true ({@link SelfTest}), where Quark's Enhanced Ladders
 * is installed too: every survival scenario checks exactly one ladder was placed and one used.
 */
final class LadderScenarios {
    /** The wall is the z = WALL_Z plane (x 2..6); ladders hang on its north side at z = WALL_Z - 1, facing north. */
    static final int WALL_Z = 6, X = 4, LADDER_Z = WALL_Z - 1;
    static final int STACK = 16;

    record Scenario(Consumer<TestArena> run, int maxTicks) {}

    static final Map<String, Scenario> ALL = new LinkedHashMap<>();
    static {
        ALL.put("looking_down_adds_one_below", new Scenario(LadderScenarios::lookingDownAddsOneBelow, 20));
        ALL.put("looking_up_adds_one_above", new Scenario(LadderScenarios::lookingUpAddsOneAbove, 20));
        ALL.put("looking_level_the_clicked_half_decides", new Scenario(LadderScenarios::lookingLevelTheClickedHalfDecides, 20));
        ALL.put("clicking_the_top_goes_up_whatever_the_look", new Scenario(LadderScenarios::clickingTheTopGoesUp, 20));
        ALL.put("works_while_sneaking", new Scenario(LadderScenarios::worksWhileSneaking, 20));
        ALL.put("goes_to_the_end_of_the_column", new Scenario(LadderScenarios::goesToTheEndOfTheColumn, 20));
        ALL.put("no_wall_above_no_ladder", new Scenario(LadderScenarios::noWallAboveNoLadder, 20));
        ALL.put("below_without_a_wall_only_hangs_with_quark", new Scenario(LadderScenarios::belowWithoutAWallOnlyHangsWithQuark, 20));
        ALL.put("creative_keeps_the_stack", new Scenario(LadderScenarios::creativeKeepsTheStack, 20));
        ALL.put("adventure_mode_cannot_build", new Scenario(LadderScenarios::adventureModeCannotBuild, 20));
        ALL.put("a_claim_cancelling_the_place_event_wins", new Scenario(LadderScenarios::aClaimCancellingThePlaceEventWins, 20));
        ALL.put("too_far_away_does_nothing", new Scenario(LadderScenarios::tooFarAwayDoesNothing, 20));
        ALL.put("blocked_spot_does_nothing", new Scenario(LadderScenarios::blockedSpotDoesNothing, 20));
        ALL.put("keeps_the_column_facing_next_to_another_wall", new Scenario(LadderScenarios::keepsTheColumnFacingNextToAnotherWall, 20));
        ALL.put("extending_into_water_waterlogs", new Scenario(LadderScenarios::extendingIntoWaterWaterlogs, 20));
        ALL.put("side_face_click_is_left_alone", new Scenario(LadderScenarios::sideFaceClickIsLeftAlone, 20));
        ALL.put("other_items_are_left_alone", new Scenario(LadderScenarios::otherItemsAreLeftAlone, 20));
    }

    /** Players whose block placements a pretend claim mod cancels (see aClaimCancellingThePlaceEventWins). */
    private static final Set<UUID> CLAIMED = new HashSet<>();
    static {
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> {
            if (e.getEntity() != null && CLAIMED.contains(e.getEntity().getUUID())) e.setCanceled(true);
        });
    }

    private LadderScenarios() {}

    static final class Climber extends FakePlayer {
        Climber(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.nameUUIDFromBytes(("localladders-" + name).getBytes()), "[" + name + "]"));
            setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LADDER, STACK));
            setGameMode(GameType.SURVIVAL);
        }

        /** Stands at (X, feetY, LADDER_Z - 2), facing the wall (south) with the given pitch (negative = up). */
        Climber at(TestArena a, double feetY, float pitch) {
            BlockPos p = a.abs(new BlockPos(X, 0, LADDER_Z - 2));
            moveTo(p.getX() + 0.5, a.abs(BlockPos.ZERO).getY() + feetY, p.getZ() + 0.5, 0.0F, pitch);
            return this;
        }

        /** Right-clicks the ladder at relative y on {@code face} at height {@code dy} within the block. */
        void click(TestArena a, int y, Direction face, double dy) {
            BlockPos pos = a.abs(new BlockPos(X, y, LADDER_Z));
            Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + dy, pos.getZ() + 13.0 / 16.0);
            gameMode.useItemOn(this, level(), getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        }

        void clickFront(TestArena a, int y, double dy) {
            click(a, y, Direction.NORTH, dy);
        }

        int held() {
            return getMainHandItem().getCount();
        }
    }

    static void wall(TestArena a, int y0, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = 2; x <= 6; x++) a.setBlock(new BlockPos(x, y, WALL_Z), Blocks.STONE);
    }

    static void ladders(TestArena a, int y0, int y1) {
        for (int y = y0; y <= y1; y++) a.setBlock(new BlockPos(X, y, LADDER_Z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
    }

    static boolean ladderAt(TestArena a, int y) {
        BlockState s = a.getBlockState(new BlockPos(X, y, LADDER_Z));
        return s.is(Blocks.LADDER) && s.getValue(LadderBlock.FACING) == Direction.NORTH;
    }

    static boolean airAt(TestArena a, int y) {
        return a.getBlockState(new BlockPos(X, y, LADDER_Z)).isAir();
    }

    /** Exactly one new ladder at y (and none past it), exactly one used. */
    static void checkOneAdded(TestArena a, Climber c, int y, int past) {
        a.check(ladderAt(a, y), "no ladder at y=" + y + " (found " + a.getBlockState(new BlockPos(X, y, LADDER_Z)) + ")");
        a.check(airAt(a, past), "a second ladder at y=" + past + " (another mod placed one too?)");
        a.check(c.held() == STACK - 1, "expected one ladder used, hand has " + c.held());
    }

    static void checkNothing(TestArena a, Climber c, int... airYs) {
        for (int y : airYs) a.check(airAt(a, y), "unexpected block at y=" + y + ": " + a.getBlockState(new BlockPos(X, y, LADDER_Z)));
        a.check(c.held() == STACK, "an item was used, hand has " + c.held());
    }

    private static void lookingDownAddsOneBelow(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        Climber c = new Climber(a.level(), "down").at(a, 6, 50);
        c.clickFront(a, 7, 0.8); // the upper half: the look wins
        a.later(1, () -> { checkOneAdded(a, c, 5, 4); a.check(airAt(a, 8), "also placed above"); a.pass(); });
    }

    private static void lookingUpAddsOneAbove(TestArena a) {
        wall(a, 1, 12); ladders(a, 3, 4);
        Climber c = new Climber(a.level(), "up").at(a, 3, -50);
        c.clickFront(a, 4, 0.2); // the lower half: the look wins
        a.later(1, () -> { checkOneAdded(a, c, 5, 6); a.check(airAt(a, 2), "also placed below"); a.pass(); });
    }

    private static void lookingLevelTheClickedHalfDecides(TestArena a) {
        wall(a, 1, 12); ladders(a, 5, 6);
        Climber c = new Climber(a.level(), "level").at(a, 5, 0);
        c.clickFront(a, 6, 0.75);
        a.later(1, () -> {
            a.check(ladderAt(a, 7) && airAt(a, 4), "upper half didn't add one above");
            c.clickFront(a, 5, 0.25);
            a.later(1, () -> {
                a.check(ladderAt(a, 4) && airAt(a, 3) && airAt(a, 8), "lower half didn't add exactly one below");
                a.check(c.held() == STACK - 2, "expected two used, hand has " + c.held());
                a.pass();
            });
        });
    }

    private static void clickingTheTopGoesUp(TestArena a) {
        wall(a, 1, 12); ladders(a, 4, 5);
        Climber c = new Climber(a.level(), "top").at(a, 6, 60);
        c.click(a, 5, Direction.UP, 1.0);
        a.later(1, () -> { checkOneAdded(a, c, 6, 7); a.check(airAt(a, 3), "also placed below"); a.pass(); });
    }

    private static void worksWhileSneaking(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        Climber c = new Climber(a.level(), "sneak").at(a, 6, 50);
        c.setShiftKeyDown(true);
        a.check(c.isShiftKeyDown(), "not sneaking");
        c.clickFront(a, 7, 0.5);
        a.later(1, () -> { checkOneAdded(a, c, 5, 4); a.pass(); });
    }

    private static void goesToTheEndOfTheColumn(TestArena a) {
        wall(a, 1, 12); ladders(a, 4, 6);
        Climber c = new Climber(a.level(), "end").at(a, 5, 50);
        c.clickFront(a, 6, 0.5);
        a.later(1, () -> { checkOneAdded(a, c, 3, 2); a.pass(); });
    }

    private static void noWallAboveNoLadder(TestArena a) {
        wall(a, 1, 6); ladders(a, 5, 6);
        Climber c = new Climber(a.level(), "nowall").at(a, 5, -50);
        c.clickFront(a, 6, 0.5);
        a.later(1, () -> { checkNothing(a, c, 7, 4, 8); a.pass(); });
    }

    /** Vanilla needs a wall behind every ladder; Quark's "Allow Freestanding" lets one hang under another. */
    private static void belowWithoutAWallOnlyHangsWithQuark(TestArena a) {
        wall(a, 8, 9); ladders(a, 8, 9);
        Climber c = new Climber(a.level(), "hang").at(a, 7, 50);
        c.clickFront(a, 9, 0.5);
        a.later(1, () -> {
            if (ModList.get().isLoaded("quark")) checkOneAdded(a, c, 7, 6);
            else checkNothing(a, c, 7, 6);
            a.pass();
        });
    }

    private static void creativeKeepsTheStack(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        Climber c = new Climber(a.level(), "creative").at(a, 6, 50);
        c.setGameMode(GameType.CREATIVE);
        c.clickFront(a, 7, 0.5);
        a.later(1, () -> {
            a.check(ladderAt(a, 5) && airAt(a, 4), "creative didn't add exactly one");
            a.check(c.held() == STACK, "creative used a ladder: " + c.held());
            a.pass();
        });
    }

    private static void adventureModeCannotBuild(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        Climber c = new Climber(a.level(), "adventure").at(a, 6, 50);
        c.setGameMode(GameType.ADVENTURE);
        c.clickFront(a, 7, 0.5);
        a.later(1, () -> { checkNothing(a, c, 5, 4, 8); a.pass(); });
    }

    private static void aClaimCancellingThePlaceEventWins(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        Climber c = new Climber(a.level(), "claim").at(a, 6, 50);
        CLAIMED.add(c.getUUID());
        try {
            c.clickFront(a, 7, 0.5);
        } finally {
            CLAIMED.remove(c.getUUID());
        }
        a.later(1, () -> { checkNothing(a, c, 5, 4, 8); a.pass(); });
    }

    private static void tooFarAwayDoesNothing(TestArena a) {
        wall(a, 0, 14); ladders(a, 3, 12);
        Climber c = new Climber(a.level(), "far").at(a, 12, 50);
        c.clickFront(a, 12, 0.5); // the end of the column (y 2) is ~10 blocks from the eyes
        a.later(1, () -> { checkNothing(a, c, 2, 13); a.pass(); });
    }

    private static void blockedSpotDoesNothing(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        a.setBlock(new BlockPos(X, 5, LADDER_Z), Blocks.DIRT);
        Climber c = new Climber(a.level(), "blocked").at(a, 6, 50);
        c.clickFront(a, 7, 0.5);
        a.later(1, () -> {
            a.check(a.getBlockState(new BlockPos(X, 5, LADDER_Z)).is(Blocks.DIRT), "the dirt was replaced");
            checkNothing(a, c, 4, 8);
            a.pass();
        });
    }

    private static void keepsTheColumnFacingNextToAnotherWall(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        a.setBlock(new BlockPos(X + 1, 5, LADDER_Z), Blocks.STONE); // a wall the new ladder could also hang on
        a.setBlock(new BlockPos(X - 1, 5, LADDER_Z), Blocks.STONE);
        Climber c = new Climber(a.level(), "facing").at(a, 6, 50);
        c.setYRot(90.0F); // looking west, at the side wall (placement goes by the head)
        c.setYHeadRot(90.0F);
        c.clickFront(a, 7, 0.5);
        a.later(1, () -> { checkOneAdded(a, c, 5, 4); a.pass(); });
    }

    private static void extendingIntoWaterWaterlogs(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        a.setBlock(new BlockPos(X, 5, LADDER_Z), Blocks.WATER);
        // Keep the water where it is: glass around and under the spot.
        a.setBlock(new BlockPos(X - 1, 5, LADDER_Z), Blocks.GLASS);
        a.setBlock(new BlockPos(X + 1, 5, LADDER_Z), Blocks.GLASS);
        a.setBlock(new BlockPos(X, 5, LADDER_Z - 1), Blocks.GLASS);
        a.setBlock(new BlockPos(X, 4, LADDER_Z), Blocks.GLASS);
        Climber c = new Climber(a.level(), "water").at(a, 6, 50);
        c.clickFront(a, 7, 0.5);
        a.later(1, () -> {
            BlockState s = a.getBlockState(new BlockPos(X, 5, LADDER_Z));
            a.check(s.is(Blocks.LADDER) && s.getValue(LadderBlock.WATERLOGGED), "expected a waterlogged ladder, got " + s);
            a.check(c.held() == STACK - 1, "expected one used, hand has " + c.held());
            a.pass();
        });
    }

    /** The side of a ladder: vanilla places one beside it on the wall (Quark, if installed, drops one to the bottom). */
    private static void sideFaceClickIsLeftAlone(TestArena a) {
        wall(a, 1, 12); ladders(a, 5, 6);
        Climber c = new Climber(a.level(), "side").at(a, 5, 0);
        c.setYRot(-60.0F);
        c.setYHeadRot(-60.0F);
        c.click(a, 6, Direction.EAST, 0.75); // upper half, level look: our extend would go up
        a.later(1, () -> {
            a.check(airAt(a, 7), "the side click extended the column upwards");
            boolean beside = a.getBlockState(new BlockPos(X + 1, 6, LADDER_Z)).is(Blocks.LADDER);
            boolean quarkBelow = ModList.get().isLoaded("quark") && ladderAt(a, 4);
            a.check(beside || quarkBelow, "neither vanilla's ladder beside it nor Quark's below");
            a.pass();
        });
    }

    private static void otherItemsAreLeftAlone(TestArena a) {
        wall(a, 1, 12); ladders(a, 6, 7);
        Climber c = new Climber(a.level(), "diamond").at(a, 6, 50);
        c.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, STACK));
        c.clickFront(a, 7, 0.5);
        a.later(1, () -> { checkNothing(a, c, 5, 8); a.pass(); });
    }
}
