package net.bdoyl.localladders.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.bdoyl.localladders.LocalLadders;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Swaps the flat model of every ladder (vanilla's and any mod's LadderBlock drawn as one flat picture, e.g. Quark's
 * wood ladders) for a 3D one: two rails standing out from the wall and rungs set between them, cut from that ladder's
 * own texture ({@link LadderShape}), so each wood keeps its colours and resource packs still apply. A ladder texture
 * that isn't rails-and-rungs (Quark's twisty crimson and warped ones) is given thickness instead, pixel by pixel like
 * a held item. Ladders whose model is already 3D (Create's metal ladders) are left alone. Purely visual: block, item,
 * shape and climbing are vanilla's.
 */
public final class Ladder3DModels {
    private static final Logger LOG = LoggerFactory.getLogger(LocalLadders.MODID);

    // Depths in the model of a north-facing ladder (wall at z = 16), in 1/16ths. Vanilla's hitbox is z 13..16.
    static final float RAIL_FRONT = 13.5F, RUNG_FRONT = 14.0F, RUNG_BACK = 15.25F;
    /** Centre and thickness of a picture given depth pixel by pixel: the leftovers between rails, or a whole odd ladder. */
    static final float LAYER = 14.6F, LAYER_THICKNESS = 1.0F, ODD_CENTRE = 14.75F, ODD_THICKNESS = 1.5F;

    private Ladder3DModels() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(Ladder3DModels::onModifyBakingResult);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        Map<TextureAtlasSprite, LadderShape> shapes = new IdentityHashMap<>();
        int swapped = 0, odd = 0;
        List<String> kept = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (!(block instanceof LadderBlock)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!state.hasProperty(LadderBlock.FACING)) continue;
                ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                BakedModel original = models.get(key);
                TextureAtlasSprite sprite = original == null ? null : flatSprite(original, state);
                if (sprite == null) {
                    String name = ForgeRegistries.BLOCKS.getKey(block).toString();
                    if (!kept.contains(name)) kept.add(name);
                    continue;
                }
                LadderShape shape = shapes.containsKey(sprite) ? shapes.get(sprite) : shapeOf(sprite);
                shapes.put(sprite, shape);
                if (shape == null) odd++;
                models.put(key, new Model(original, bake(shape, sprite, state.getValue(LadderBlock.FACING))));
                swapped++;
            }
        }
        LOG.info("[localladders] 3D ladder models: {} block states swapped ({} with an odd texture, given depth pixel by pixel); left as they were: {}",
                swapped, odd, kept.isEmpty() ? "none" : String.join(", ", kept));
    }

    /** The one texture of a model that is a single flat picture parallel to the wall, else null. */
    @Nullable
    static TextureAtlasSprite flatSprite(BakedModel model, BlockState state) {
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, RandomSource.create(42L)));
        for (Direction d : Direction.values()) quads.addAll(model.getQuads(state, d, RandomSource.create(42L)));
        if (quads.isEmpty() || quads.size() > 4) return null;
        int axis = state.getValue(LadderBlock.FACING).getAxis() == Direction.Axis.X ? 0 : 2;
        TextureAtlasSprite sprite = quads.get(0).getSprite();
        float plane = Float.NaN;
        for (BakedQuad q : quads) {
            if (q.getSprite() != sprite) return null;
            int[] v = q.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) {
                float c = Float.intBitsToFloat(v[i * stride + axis]);
                if (Float.isNaN(plane)) plane = c;
                else if (Math.abs(c - plane) > 1.0E-3F) return null;
            }
        }
        return sprite;
    }

    @Nullable
    private static LadderShape shapeOf(TextureAtlasSprite sprite) {
        SpriteContents c = sprite.contents();
        try {
            return LadderShape.of(new LadderShape.Pixels() {
                @Override public int width() { return c.width(); }
                @Override public int height() { return c.height(); }
                @Override public boolean opaque(int x, int y) { return !c.isTransparent(0, x, y); }
            });
        } catch (RuntimeException e) { // a texture we can't read: keep that ladder flat
            LOG.warn("[localladders] can't read {}: {}", c.name(), e.toString());
            return null;
        }
    }

    /** Quads by cull side (null = always drawn) for one facing; a null shape = the whole picture with depth. */
    static Map<Direction, List<BakedQuad>> bake(@Nullable LadderShape shape, TextureAtlasSprite sprite, Direction facing) {
        Baker b = new Baker(sprite, facing);
        if (shape == null) {
            b.extruded(ODD_CENTRE, ODD_THICKNESS);
            return b.quads;
        }
        for (LadderShape.Span rail : List.of(shape.leftRail(), shape.rightRail())) {
            // Model x runs opposite to texture u on the front face (u 0 is at x 16), v 0 is the top.
            float x0 = 16 - rail.to(), x1 = 16 - rail.from();
            Vector3f from = new Vector3f(x0, 0, RAIL_FRONT), to = new Vector3f(x1, 16, 16);
            b.face(from, to, Direction.NORTH, rail.from(), 0, rail.to(), 16, false);
            b.face(from, to, Direction.SOUTH, rail.to(), 0, rail.from(), 16, true);
            b.face(from, to, Direction.EAST, rail.from(), 0, rail.to(), 16, false);
            b.face(from, to, Direction.WEST, rail.from(), 0, rail.to(), 16, false);
            b.face(from, to, Direction.UP, rail.to(), 0, rail.from(), 1, false);
            b.face(from, to, Direction.DOWN, rail.to(), 15, rail.from(), 16, false);
        }
        for (LadderShape.Rung r : shape.rungs()) {
            Vector3f from = new Vector3f(16 - r.u1(), 16 - r.v1(), RUNG_FRONT), to = new Vector3f(16 - r.u0(), 16 - r.v0(), RUNG_BACK);
            float px = Math.min(1, r.v1() - r.v0());
            b.face(from, to, Direction.NORTH, r.u0(), r.v0(), r.u1(), r.v1(), false);
            b.face(from, to, Direction.SOUTH, r.u1(), r.v0(), r.u0(), r.v1(), false);
            b.face(from, to, Direction.UP, r.u1(), r.v0(), r.u0(), r.v0() + px, false);
            b.face(from, to, Direction.DOWN, r.u1(), r.v1() - px, r.u0(), r.v1(), false);
            b.face(from, to, Direction.EAST, r.u0(), r.v0(), r.u0() + 1, r.v1(), false);
            b.face(from, to, Direction.WEST, r.u1() - 1, r.v0(), r.u1(), r.v1(), false);
        }
        // The rest of the picture (vines, knots, broken bits), hidden inside the rails and rungs where they are.
        if (shape.hasLeftovers()) b.extruded(LAYER, LAYER_THICKNESS);
        return b.quads;
    }

    private static final class Baker {
        static final FaceBakery BAKERY = new FaceBakery();
        final TextureAtlasSprite sprite;
        final Direction facing;
        final BlockModelRotation rotation, turnedRound;
        final Map<Direction, List<BakedQuad>> quads = new HashMap<>();

        Baker(TextureAtlasSprite sprite, Direction facing) {
            this.sprite = sprite;
            this.facing = facing;
            // Same rotations as vanilla's ladder blockstate file (the model is drawn facing north).
            int y = switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
            this.rotation = BlockModelRotation.by(0, y);
            this.turnedRound = BlockModelRotation.by(0, (y + 180) % 360);
        }

        /**
         * The whole picture with depth, as vanilla gives a held item (front, back and an edge around every pixel),
         * {@code thickness}/16 thick and centred at z = {@code centre}. Item geometry faces south, so it is built
         * turned round (z mirrored) and baked with a half turn more.
         */
        void extruded(float centre, float thickness) {
            float mirroredCentre = 16 - centre;
            for (BlockElement e : new ItemModelGenerator().processFrames(0, "#texture", sprite.contents())) {
                Vector3f from = new Vector3f(e.from.x(), e.from.y(), mirroredCentre + (e.from.z() - 8) * thickness);
                Vector3f to = new Vector3f(e.to.x(), e.to.y(), mirroredCentre + (e.to.z() - 8) * thickness);
                e.faces.forEach((side, f) -> quads.computeIfAbsent(null, d -> new ArrayList<>()).add(BAKERY.bakeQuad(new Vector3f(from), new Vector3f(to),
                        new BlockElementFace(null, -1, "#texture", f.uv), sprite, side, turnedRound, null, true, new ResourceLocation(LocalLadders.MODID, "ladder_3d"))));
            }
        }

        /** One face of the box from..to (north-facing model), textured with the given texture rectangle. */
        void face(Vector3f from, Vector3f to, Direction side, float u0, float v0, float u1, float v1, boolean againstWall) {
            BlockElementFace f = new BlockElementFace(null, -1, "#texture", new BlockFaceUV(new float[]{u0, v0, u1, v1}, 0));
            BakedQuad q = BAKERY.bakeQuad(new Vector3f(from), new Vector3f(to), f, sprite, side, rotation, null, true,
                    new ResourceLocation(LocalLadders.MODID, "ladder_3d"));
            // The back faces touch the wall: skipped like any face against a solid block (glass walls still show them).
            quads.computeIfAbsent(againstWall ? facing.getOpposite() : null, d -> new ArrayList<>()).add(q);
        }
    }

    /** The original model (render type, particles, item transforms) with our quads. */
    static final class Model extends BakedModelWrapper<BakedModel> {
        private final Map<Direction, List<BakedQuad>> bySide = new EnumMap<>(Direction.class);
        private final List<BakedQuad> general;

        Model(BakedModel original, Map<Direction, List<BakedQuad>> quads) {
            super(original);
            quads.forEach((d, q) -> { if (d != null) bySide.put(d, List.copyOf(q)); });
            general = List.copyOf(quads.getOrDefault(null, List.of()));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return side == null ? general : bySide.getOrDefault(side, List.of());
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
            return getQuads(state, side, rand);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false; // like vanilla's ladder; small parts with smooth lighting just look dirty
        }
    }
}
