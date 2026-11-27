package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.raster.RasterDraw;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splits a themed page into what holds still and what moves by painting it once a game tick over a long stretch of the
 * page clock. Pixels that never change go to the still layer; the rest gather into regions, each with a loop of its own
 * taken from its pixels. A region's frames carry the whole pixel, page art under the motion included, and the still layer
 * is left clear under them, so the two never need a draw order.
 * <p>A region's loop is chosen cell by cell: each 8x8 cell of it that repeats exactly has a period, and the loop is the
 * shortest length that as many moving pixels as possible repeat on, so a swaying vine keeps its sway and a foot scene
 * keeps its whole act even where a particle or a per-act variation never repeats. A loop's frames must fit
 * {@link #STRIP_BUDGET}; a region with nothing periodic gets the length its look most often recurs at.
 */
public final class LayerBake {
    static final int TICK = 50;
    /** Samples one tick apart; long enough to see the longest loop twice. */
    static final int MAX_LOOP = 800;
    static final int SAMPLES = 2 * MAX_LOOP;
    static final int MIN_LOOP = 8;
    static final int CELL = 8;
    /** Distinct frame pixels one texture piece may carry. */
    static final long STRIP_BUDGET = 1L << 17;
    /** How much better a longer length must match before a region with nothing periodic takes it. */
    static final double GAIN = 0.01;

    private LayerBake() {
    }

    /**
     * How a region's loop closes: {@code EXACT} repeats every moving pixel exactly; {@code NEAREST} repeats what can be
     * repeated and closes where the rest matches longest, so a seam may show in what never repeats.
     */
    public enum Closure {
        EXACT,
        NEAREST
    }

    /** A loop over sample indices: it starts at {@code start}, lasts {@code length}, and closes as {@code closure} says. */
    record Loop(int start, int length, Closure closure) {
    }

    public static BakedLayer bake(ThemeAssets assets, PageSpec spec, int leaf) {
        KitRender render = new KitRender(assets, spec, leaf);
        int width = render.width();
        int height = render.height();
        Raster base = render.base();

        Raster first = base.copy();
        render.moving(first, 0L, null);
        boolean[] varies = new boolean[width * height];
        Raster work = base.copy();
        for (int sample = 1; sample < SAMPLES; sample++) {
            System.arraycopy(base.pixels(), 0, work.pixels(), 0, base.pixels().length);
            render.moving(work, (long) sample * TICK, null);
            int[] now = work.pixels();
            int[] then = first.pixels();
            for (int at = 0; at < now.length; at++) {
                if (now[at] != then[at]) {
                    varies[at] = true;
                }
            }
        }

        Raster still = first.copy();
        for (int at = 0; at < varies.length; at++) {
            if (varies[at]) {
                still.pixels()[at] = 0;
            }
        }

        List<int[]> boxes = regions(varies, width, height);
        List<Tracks> tracks = new ArrayList<>();
        for (int[] box : boxes) {
            tracks.add(new Tracks(box, varies, width));
        }
        for (int sample = 0; sample < SAMPLES; sample++) {
            System.arraycopy(base.pixels(), 0, work.pixels(), 0, base.pixels().length);
            render.moving(work, (long) sample * TICK, null);
            for (Tracks track : tracks) {
                track.record(sample, work.pixels());
            }
        }

        List<Motion> motions = new ArrayList<>();
        for (int region = 0; region < boxes.size(); region++) {
            Loop loop = tracks.get(region).loop();
            motions.addAll(frames(render, base, work, boxes.get(region), varies, width, loop));
        }
        return new BakedLayer(width, height, still, motions);
    }

    /** Boxes round the connected moving pixels, grown by one pixel and merged where they touch. */
    static List<int[]> regions(boolean[] varies, int width, int height) {
        boolean[] grown = new boolean[varies.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!varies[y * width + x]) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < width && ny < height) {
                            grown[ny * width + nx] = true;
                        }
                    }
                }
            }
        }
        boolean[] seen = new boolean[varies.length];
        List<int[]> boxes = new ArrayList<>();
        Deque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < grown.length; start++) {
            if (!grown[start] || seen[start]) {
                continue;
            }
            int x0 = Integer.MAX_VALUE;
            int y0 = Integer.MAX_VALUE;
            int x1 = -1;
            int y1 = -1;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int at = queue.poll();
                int x = at % width;
                int y = at / width;
                if (varies[at]) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < width && ny < height && grown[ny * width + nx] && !seen[ny * width + nx]) {
                            seen[ny * width + nx] = true;
                            queue.add(ny * width + nx);
                        }
                    }
                }
            }
            if (x1 >= 0) {
                boxes.add(new int[]{x0, y0, x1 + 1, y1 + 1});
            }
        }
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int a = 0; a < boxes.size() && !merged; a++) {
                for (int b = a + 1; b < boxes.size() && !merged; b++) {
                    int[] p = boxes.get(a);
                    int[] q = boxes.get(b);
                    if (p[0] <= q[2] && q[0] <= p[2] && p[1] <= q[3] && q[1] <= p[3]) {
                        boxes.set(a, new int[]{Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.max(p[2], q[2]), Math.max(p[3], q[3])});
                        boxes.remove(b);
                        merged = true;
                    }
                }
            }
        }
        boxes.sort((int[] p, int[] q) -> p[1] != q[1] ? Integer.compare(p[1], q[1]) : Integer.compare(p[0], q[0]));
        return boxes;
    }

    /**
     * A region's looks over the samples, kept as hashes: of the whole region, of each 8x8 cell, and of each texture piece
     * the writer will cut it into.
     */
    static final class Tracks {
        private final int[][] cells;
        private final int[][] pieces;
        private final long[][] cellLooks;
        private final long[][] pieceLooks;
        private final long[] looks;

        Tracks(int[] box, boolean[] varies, int width) {
            cells = groups(box, varies, width, CELL, CELL);
            int w = box[2] - box[0];
            int h = box[3] - box[1];
            int columns = (w + KitBake.PIECE - 1) / KitBake.PIECE;
            int rows = (h + KitBake.PIECE - 1) / KitBake.PIECE;
            pieces = groups(box, varies, width, (w + columns - 1) / columns, (h + rows - 1) / rows);
            cellLooks = new long[cells.length][SAMPLES];
            pieceLooks = new long[pieces.length][SAMPLES];
            looks = new long[SAMPLES];
        }

        /** The moving pixels of {@code box} in groups of tiles {@code tileWidth} by {@code tileHeight}, empty tiles left out. */
        private static int[][] groups(int[] box, boolean[] varies, int width, int tileWidth, int tileHeight) {
            List<int[]> groups = new ArrayList<>();
            for (int y0 = box[1]; y0 < box[3]; y0 += tileHeight) {
                for (int x0 = box[0]; x0 < box[2]; x0 += tileWidth) {
                    List<Integer> found = new ArrayList<>();
                    for (int y = y0; y < Math.min(y0 + tileHeight, box[3]); y++) {
                        for (int x = x0; x < Math.min(x0 + tileWidth, box[2]); x++) {
                            if (varies[y * width + x]) {
                                found.add(y * width + x);
                            }
                        }
                    }
                    if (!found.isEmpty()) {
                        groups.add(found.stream().mapToInt(Integer::intValue).toArray());
                    }
                }
            }
            return groups.toArray(new int[0][]);
        }

        void record(int sample, int[] pixels) {
            long whole = 0xcbf29ce484222325L;
            for (int cell = 0; cell < cells.length; cell++) {
                long look = hash(cells[cell], pixels);
                cellLooks[cell][sample] = look;
                whole = (whole ^ look) * 0x100000001b3L;
            }
            looks[sample] = whole;
            for (int piece = 0; piece < pieces.length; piece++) {
                pieceLooks[piece][sample] = hash(pieces[piece], pixels);
            }
        }

        private static long hash(int[] at, int[] pixels) {
            long hash = 0xcbf29ce484222325L;
            for (int index : at) {
                hash = (hash ^ pixels[index]) * 0x100000001b3L;
            }
            return hash;
        }

        /**
         * The loop: among lengths up to {@link #MAX_LOOP} whose frames fit the budget, the one that the most moving pixels
         * repeat on exactly (the shortest of equals); failing any repeating cell, the length the region's look recurs at
         * most often. It starts in the longest stretch where the samples one loop apart agree, so the seam falls where
         * the region repeats.
         */
        Loop loop() {
            int[] periods = new int[cells.length];
            long total = 0;
            for (int cell = 0; cell < cells.length; cell++) {
                periods[cell] = period(cellLooks[cell], MAX_LOOP);
                total += cells[cell].length;
            }
            long[] cover = new long[MAX_LOOP + 1];
            for (int length = MIN_LOOP; length <= MAX_LOOP; length++) {
                for (int cell = 0; cell < cells.length; cell++) {
                    if (periods[cell] > 0 && length % periods[cell] == 0) {
                        cover[length] += cells[cell].length;
                    }
                }
            }
            List<Integer> order = new ArrayList<>();
            for (int length = MIN_LOOP; length <= MAX_LOOP; length++) {
                order.add(length);
            }
            boolean periodic = order.stream().anyMatch((Integer length) -> cover[length] > 0);
            if (periodic) {
                order.sort((Integer a, Integer b) -> cover[a] != cover[b] ? Long.compare(cover[b], cover[a]) : Integer.compare(a, b));
                for (int length : order) {
                    if (cover[length] == 0) {
                        break;
                    }
                    Closure closure = cover[length] == total ? Closure.EXACT : Closure.NEAREST;
                    Loop loop = new Loop(closure == Closure.EXACT ? 0 : seam(looks, length), length, closure);
                    if (fits(loop)) {
                        return loop;
                    }
                }
            }
            for (int length : recurring(cellLooks, cells)) {
                Loop loop = new Loop(seam(looks, length), length, Closure.NEAREST);
                if (fits(loop)) {
                    return loop;
                }
            }
            return new Loop(0, MIN_LOOP, Closure.NEAREST);
        }

        /** Whether every piece's distinct frames over {@code loop} stay within {@link #STRIP_BUDGET}. */
        private boolean fits(Loop loop) {
            for (int piece = 0; piece < pieces.length; piece++) {
                Set<Long> distinct = new HashSet<>();
                for (int sample = loop.start(); sample < loop.start() + loop.length(); sample++) {
                    distinct.add(pieceLooks[piece][sample]);
                }
                if ((long) distinct.size() * pieces[piece].length > STRIP_BUDGET) {
                    return false;
                }
            }
            return true;
        }
    }

    /** The shortest {@code length} up to {@code max} that {@code looks} repeat on over the whole run, or 0. */
    static int period(long[] looks, int max) {
        for (int length = 1; length <= max && length < looks.length; length++) {
            boolean repeats = true;
            for (int i = 0; i + length < looks.length && repeats; i++) {
                repeats = looks[i] == looks[i + length];
            }
            if (repeats) {
                return length;
            }
        }
        return 0;
    }

    /**
     * Lengths for what does not repeat exactly, best first: each length on from {@link #MIN_LOOP} whose looks one length
     * later agree, cell by cell and weighted by the cell's moving pixels, at least {@link #GAIN} more often than at every
     * shorter length.
     */
    static List<Integer> recurring(long[][] looks, int[][] cells) {
        List<Integer> better = new ArrayList<>();
        double best = -1;
        int samples = looks[0].length;
        long total = 0;
        for (int[] cell : cells) {
            total += cell.length;
        }
        for (int length = MIN_LOOP; length <= MAX_LOOP && length < samples; length++) {
            double agree = 0;
            for (int cell = 0; cell < looks.length; cell++) {
                int same = 0;
                for (int i = 0; i + length < samples; i++) {
                    if (looks[cell][i] == looks[cell][i + length]) {
                        same++;
                    }
                }
                agree += same * (double) cells[cell].length;
            }
            double match = agree / total / (samples - length);
            if (match > best + GAIN) {
                best = match;
                better.add(0, length);
            }
        }
        return better;
    }

    /** The start of the longest stretch where {@code looks} agree with themselves {@code length} later; 0 when none do. */
    static int seam(long[] looks, int length) {
        int run = 0;
        int longest = 0;
        int start = 0;
        for (int i = 0; i + length < looks.length; i++) {
            run = looks[i] == looks[i + length] ? run + 1 : 0;
            if (run > longest) {
                longest = run;
                start = i - run + 1;
            }
        }
        return Math.min(start, looks.length - 1 - length);
    }

    /**
     * The region's frames, one per tick over the loop, in pieces no larger than {@link KitBake#PIECE} each way so no one
     * texture carries frames for a whole wide region; the pieces share the loop, so they stay in step.
     */
    private static List<Motion> frames(KitRender render, Raster base, Raster work, int[] box, boolean[] varies, int width, Loop loop) {
        int ticks = loop.length();
        double loopMillis = (double) ticks * TICK;
        long startMillis = (long) loop.start() * TICK;
        int w = box[2] - box[0];
        int columns = (w + KitBake.PIECE - 1) / KitBake.PIECE;
        int rows = (box[3] - box[1] + KitBake.PIECE - 1) / KitBake.PIECE;
        List<int[]> parts = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int[] part = moving(varies, width, box[0] + w * column / columns, box[1] + (box[3] - box[1]) * row / rows,
                        box[0] + w * (column + 1) / columns, box[1] + (box[3] - box[1]) * (row + 1) / rows);
                if (part != null) {
                    parts.add(part);
                }
            }
        }
        List<List<Raster>> unique = new ArrayList<>();
        List<Map<Integer, List<Integer>>> known = new ArrayList<>();
        List<List<int[]>> sequences = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            unique.add(new ArrayList<>());
            known.add(new HashMap<>());
            sequences.add(new ArrayList<>());
        }
        RasterDraw.Clip clip = new RasterDraw.Clip(box[0], box[1], box[2], box[3]);
        for (int tick = 0; tick < ticks; tick++) {
            for (int y = box[1]; y < box[3]; y++) {
                System.arraycopy(base.pixels(), y * width + box[0], work.pixels(), y * width + box[0], w);
            }
            render.moving(work, millisAt(startMillis, loopMillis, ticks, tick), clip);
            for (int index = 0; index < parts.size(); index++) {
                int[] part = parts.get(index);
                Raster frame = new Raster(part[2] - part[0], part[3] - part[1]);
                for (int y = part[1]; y < part[3]; y++) {
                    for (int x = part[0]; x < part[2]; x++) {
                        if (varies[y * width + x]) {
                            frame.set(x - part[0], y - part[1], work.pixels()[y * width + x]);
                        }
                    }
                }
                List<Raster> frames = unique.get(index);
                List<Integer> candidates = known.get(index).computeIfAbsent(Arrays.hashCode(frame.pixels()), (Integer key) -> new ArrayList<>());
                int found = -1;
                for (int candidate : candidates) {
                    if (frames.get(candidate).same(frame)) {
                        found = candidate;
                        break;
                    }
                }
                if (found < 0) {
                    frames.add(frame);
                    found = frames.size() - 1;
                    candidates.add(found);
                }
                List<int[]> sequence = sequences.get(index);
                if (!sequence.isEmpty() && sequence.get(sequence.size() - 1)[0] == found) {
                    sequence.get(sequence.size() - 1)[1]++;
                } else {
                    sequence.add(new int[]{found, 1});
                }
            }
        }
        List<Motion> motions = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            int[] part = parts.get(index);
            int pw = part[2] - part[0];
            int ph = part[3] - part[1];
            boolean[] mask = new boolean[pw * ph];
            for (int y = 0; y < ph; y++) {
                for (int x = 0; x < pw; x++) {
                    mask[y * pw + x] = varies[(part[1] + y) * width + part[0] + x];
                }
            }
            motions.add(new Motion(part[0], part[1], pw, ph, unique.get(index), sequences.get(index), ticks, loop.closure(), startMillis,
                    loopMillis, mask));
        }
        return motions;
    }

    /** The box round the moving pixels inside ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}), or null when none move. */
    private static int[] moving(boolean[] varies, int width, int x0, int y0, int x1, int y1) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (varies[y * width + x]) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x + 1);
                    bottom = Math.max(bottom, y + 1);
                }
            }
        }
        return right <= left ? null : new int[]{left, top, right, bottom};
    }

    /** The page clock time a loop starting at {@code startMillis} shows at {@code tick}. */
    static long millisAt(long startMillis, double loopMillis, int ticks, long tick) {
        return Math.round(startMillis + Math.floorMod(tick, (long) ticks) * (loopMillis / ticks));
    }

    /**
     * One moving region on the canvas: its box, its distinct frames, the order they show in as (frame, ticks) pairs, the
     * loop's length in ticks, how the loop closes, where the loop starts and how long it is on the page clock, and which
     * of its pixels move.
     */
    public record Motion(int x, int y, int width, int height, List<Raster> frames, List<int[]> sequence, int ticks, Closure closure,
                         long startMillis, double sourceMillis, boolean[] mask) {
        public Motion {
            frames = List.copyOf(frames);
            List<int[]> copy = new ArrayList<>();
            for (int[] step : sequence) {
                copy.add(Arrays.copyOf(step, 2));
            }
            sequence = List.copyOf(copy);
        }

        /**
         * The part of this motion inside canvas box ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}), trimmed to its
         * moving pixels, with its distinct frames counted again; null when nothing moves there.
         */
        public Motion part(int x0, int y0, int x1, int y1) {
            int left = Integer.MAX_VALUE;
            int top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int bottom = Integer.MIN_VALUE;
            for (int row = Math.max(y, y0); row < Math.min(y + height, y1); row++) {
                for (int column = Math.max(x, x0); column < Math.min(x + width, x1); column++) {
                    if (mask[(row - y) * width + column - x]) {
                        left = Math.min(left, column);
                        top = Math.min(top, row);
                        right = Math.max(right, column + 1);
                        bottom = Math.max(bottom, row + 1);
                    }
                }
            }
            if (right <= left) {
                return null;
            }
            int w = right - left;
            int h = bottom - top;
            boolean[] kept = new boolean[w * h];
            for (int row = 0; row < h; row++) {
                for (int column = 0; column < w; column++) {
                    kept[row * w + column] = mask[(top - y + row) * width + left - x + column];
                }
            }
            List<Raster> distinct = new ArrayList<>();
            int[] remap = new int[frames.size()];
            for (int index = 0; index < frames.size(); index++) {
                Raster cropped = frames.get(index).crop(left - x, top - y, w, h);
                int found = -1;
                for (int known = 0; known < distinct.size(); known++) {
                    if (distinct.get(known).same(cropped)) {
                        found = known;
                        break;
                    }
                }
                if (found < 0) {
                    distinct.add(cropped);
                    found = distinct.size() - 1;
                }
                remap[index] = found;
            }
            List<int[]> steps = new ArrayList<>();
            for (int[] step : sequence) {
                int frame = remap[step[0]];
                if (!steps.isEmpty() && steps.get(steps.size() - 1)[0] == frame) {
                    steps.get(steps.size() - 1)[1] += step[1];
                } else {
                    steps.add(new int[]{frame, step[1]});
                }
            }
            return new Motion(left, top, w, h, distinct, steps, ticks, closure, startMillis, sourceMillis, kept);
        }

        /** The page clock time whose look this motion's frame at {@code tick} shows. */
        public long millisAt(long tick) {
            return LayerBake.millisAt(startMillis, sourceMillis, ticks, tick);
        }

        /** This motion in pieces no larger than {@code size} each way, each trimmed to what moves in it. */
        public List<Motion> pieces(int size) {
            List<Motion> pieces = new ArrayList<>();
            int columns = (width + size - 1) / size;
            int rows = (height + size - 1) / size;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    Motion piece = part(x + width * column / columns, y + height * row / rows, x + width * (column + 1) / columns,
                            y + height * (row + 1) / rows);
                    if (piece != null) {
                        pieces.add(piece);
                    }
                }
            }
            return pieces;
        }
    }
}
