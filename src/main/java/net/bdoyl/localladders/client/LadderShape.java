package net.bdoyl.localladders.client;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Where the rails and rungs are in a ladder texture, read from its opaque pixels, so the 3D model is cut from each
 * ladder's own picture (vanilla, Quark's woods, a resource pack's): rails = the widest run of fully opaque columns in
 * each half, rungs = rows that are opaque all the way between the rails (as wide as they stay opaque). Anything else
 * that is drawn (vines, knots, broken bits) is kept as a flat layer between the rails. All numbers are in 1/16ths of
 * the texture (the units of block model UVs), u from the left and v from the top.
 */
record LadderShape(Span leftRail, Span rightRail, List<Rung> rungs, boolean hasLeftovers) {
    record Span(float from, float to) {}

    record Rung(float u0, float u1, float v0, float v1) {}

    interface Pixels {
        int width();

        int height();

        boolean opaque(int x, int y);
    }

    /** The shape, or null when the texture doesn't look like a ladder with two rails and at least one rung. */
    @Nullable
    static LadderShape of(Pixels px) {
        int w = px.width(), h = px.height();
        if (w < 4 || h < 4) return null;
        boolean[] fullCol = new boolean[w];
        for (int x = 0; x < w; x++) {
            boolean full = true;
            for (int y = 0; y < h && full; y++) full = px.opaque(x, y);
            fullCol[x] = full;
        }
        int[] left = widestRun(fullCol, 0, w / 2), right = widestRun(fullCol, w / 2, w);
        if (left == null || right == null || right[0] - left[1] < 2) return null;

        float s = 16.0F / w, t = 16.0F / h;
        boolean[][] covered = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = left[0]; x < left[1]; x++) covered[x][y] = true;
            for (int x = right[0]; x < right[1]; x++) covered[x][y] = true;
        }
        List<Rung> rungs = new ArrayList<>();
        int y = 0;
        while (y < h) {
            if (!rowOpaque(px, y, left[1], right[0])) { y++; continue; }
            int y0 = y;
            while (y < h && rowOpaque(px, y, left[1], right[0])) y++;
            // As wide as every row of this rung stays opaque, so a rung may poke out past the rails like vanilla's.
            int u0 = left[1], u1 = right[0];
            while (u0 > 0 && colOpaque(px, u0 - 1, y0, y)) u0--;
            while (u1 < w && colOpaque(px, u1, y0, y)) u1++;
            for (int yy = y0; yy < y; yy++) for (int x = u0; x < u1; x++) covered[x][yy] = true;
            rungs.add(new Rung(u0 * s, u1 * s, y0 * t, y * t));
        }
        if (rungs.isEmpty()) return null;
        boolean leftovers = false;
        for (int x = 0; x < w && !leftovers; x++) for (int yy = 0; yy < h && !leftovers; yy++) leftovers = px.opaque(x, yy) && !covered[x][yy];
        return new LadderShape(new Span(left[0] * s, left[1] * s), new Span(right[0] * s, right[1] * s), rungs, leftovers);
    }

    /** [from, to) of the widest run of true in cols[from, to), the one nearest the edge on a tie; null if none. */
    @Nullable
    private static int[] widestRun(boolean[] cols, int from, int to) {
        int[] best = null;
        boolean preferLater = from > 0; // the right half: nearer the right edge
        for (int x = from; x < to; ) {
            if (!cols[x]) { x++; continue; }
            int start = x;
            while (x < to && cols[x]) x++;
            int len = x - start;
            if (best == null || len > best[1] - best[0] || (len == best[1] - best[0] && preferLater)) best = new int[]{start, x};
        }
        return best;
    }

    private static boolean rowOpaque(Pixels px, int y, int x0, int x1) {
        for (int x = x0; x < x1; x++) if (!px.opaque(x, y)) return false;
        return true;
    }

    private static boolean colOpaque(Pixels px, int x, int y0, int y1) {
        for (int y = y0; y < y1; y++) if (!px.opaque(x, y)) return false;
        return true;
    }
}
