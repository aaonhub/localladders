package net.bdoyl.localladders;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Right-click a ladder with a ladder (sneaking or not) and one ladder is added at the end of that column: the first
 * free spot above or below it, facing the same way. Which end:
 * <ul>
 *   <li>clicked the top or bottom of the ladder: that end;</li>
 *   <li>looking up or down more than {@link #PITCH_DEGREES}: up or down;</li>
 *   <li>looking roughly straight at it: the upper half of the ladder you clicked means up, the lower half down.</li>
 * </ul>
 * The ladder is placed exactly like a normal right-click on that spot (the held ladder item's own placement, Forge's
 * place event for claim mods, spawn protection, adventure mode, one item used outside creative), so it needs whatever
 * a normal ladder there needs: a solid wall behind it (or a ladder above it to hang from, where a mod like Quark allows
 * that) and nobody standing in the way. Clicking a ladder's side face keeps the vanilla behaviour.
 */
public final class LadderExtender {
    /** Looking further up/down than this picks the direction by itself; closer to level, where you click decides. */
    static final float PITCH_DEGREES = 20.0F;
    /** How far past the player's normal block reach the new ladder may be (eyes to the centre of its spot). */
    static final double EXTRA_REACH = 1.5;
    /** Longest column walked to find its end. */
    static final int MAX_COLUMN = 64;

    /** Ladders whose own mod already does something on right-click with a ladder (Create's metal ladders). */
    static final TagKey<Block> NOT_EXTENDABLE = TagKey.create(Registries.BLOCK, new ResourceLocation(LocalLadders.MODID, "not_extendable"));

    private LadderExtender() {}

    // HIGH: before Quark's Enhanced Ladders (which would always drop the ladder to the bottom), so a click places one.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player.isSpectator() || !isLadder(stack) || event.getUseItem() == Event.Result.DENY) return;
        Level level = event.getLevel();
        BlockHitResult hit = event.getHitVec();
        BlockState clicked = level.getBlockState(event.getPos());
        if (!(clicked.getBlock() instanceof LadderBlock) || !clicked.hasProperty(LadderBlock.FACING) || clicked.is(NOT_EXTENDABLE)) return;
        Direction facing = clicked.getValue(LadderBlock.FACING);
        Direction face = hit.getDirection();
        if (face != facing && face.getAxis().isHorizontal()) return; // a side face: vanilla (ladders side by side)

        Direction way = direction(player, hit, event.getPos(), facing);
        BlockPos target = columnEnd(level, event.getPos(), facing, way);
        boolean placed = target != null && player.canReach(target, EXTRA_REACH)
                && (level.isClientSide ? looksPlaceable(level, target, facing, stack) : place((ServerPlayer) player, event.getHand(), stack, target, facing));
        // Either way this click was an extend attempt, so nothing else (vanilla, Quark) acts on it.
        event.setCanceled(true);
        event.setCancellationResult(placed ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.FAIL);
    }

    static boolean isLadder(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof LadderBlock;
    }

    /** Up or down, see the class comment. */
    static Direction direction(Player player, BlockHitResult hit, BlockPos clicked, Direction facing) {
        if (hit.getDirection() == Direction.UP || hit.getDirection() == Direction.DOWN) return hit.getDirection();
        float pitch = player.getXRot(); // negative = looking up
        if (pitch <= -PITCH_DEGREES) return Direction.UP;
        if (pitch >= PITCH_DEGREES) return Direction.DOWN;
        return hit.getLocation().y - clicked.getY() >= 0.5 ? Direction.UP : Direction.DOWN;
    }

    /** The first spot past the end of the column (ladders facing {@code facing}) going {@code way}, or null if none. */
    @Nullable
    static BlockPos columnEnd(Level level, BlockPos start, Direction facing, Direction way) {
        BlockPos pos = start;
        for (int i = 0; i < MAX_COLUMN; i++) {
            pos = pos.relative(way);
            if (level.isOutsideBuildHeight(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof LadderBlock) || !state.hasProperty(LadderBlock.FACING) || state.getValue(LadderBlock.FACING) != facing)
                return pos;
        }
        return null;
    }

    /** Client-side guess (for the arm swing only; the server decides). */
    private static boolean looksPlaceable(Level level, BlockPos target, Direction facing, ItemStack stack) {
        BlockState ladder = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
        return level.getBlockState(target).canBeReplaced() && ladder.hasProperty(LadderBlock.FACING)
                && ladder.setValue(LadderBlock.FACING, facing).canSurvive(level, target);
    }

    /**
     * Uses the held ladder on {@code target} as a normal right-click there would: {@code ItemStack.useOn}, i.e. Forge's
     * placement hook (block place event, adventure mode), the ladder's own getStateForPlacement and canSurvive, sound,
     * stats, and one item used unless in creative. For the duration of the call the player looks level at the wall
     * behind the spot, so the ladder's placement picks the column's facing even when the spot has other walls too.
     */
    static boolean place(ServerPlayer player, InteractionHand hand, ItemStack stack, BlockPos target, Direction facing) {
        Level level = player.level();
        if (!level.getBlockState(target).canBeReplaced() || !level.mayInteract(player, target) || !player.mayUseItemAt(target, facing, stack))
            return false;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), facing, target, false);
        float yRot = player.getYRot(), headRot = player.getYHeadRot(), xRot = player.getXRot();
        try {
            // Placement reads the head's yaw (LivingEntity.getViewYRot), so turn that too.
            player.setYRot(facing.getOpposite().toYRot());
            player.setYHeadRot(facing.getOpposite().toYRot());
            player.setXRot(0.0F);
            InteractionResult result = stack.useOn(new UseOnContext(player, hand, hit));
            if (!result.consumesAction()) return false;
        } finally {
            player.setYRot(yRot);
            player.setYHeadRot(headRot);
            player.setXRot(xRot);
        }
        BlockState now = level.getBlockState(target);
        return now.getBlock() instanceof LadderBlock && now.getValue(LadderBlock.FACING) == facing;
    }
}
