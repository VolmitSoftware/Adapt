package art.arcane.adapt.client.theme;

/**
 * Each skill's own hover frame, drawn on a {@link FrameCanvas}: its assembly as {@code e} runs 0 to 1, an idle loop on
 * {@code t} and its own way of leaving while {@code out} is set.
 */
final class HoverFrames {
    private static final int PINION_FRAMES = 3;
    private static final String[] PINION_KEYS = {"g0", "g1", "g2"};
    private static final int[] HERBALISM_WAVE = {0, 0, 1, 1, 0, 0, 0, 1};
    private static final int[] HERBALISM_LEAF_STEP = {9, 13, 8, 11, 15};
    private static final int[] SEABORNE_WAVE = {0, 0, 1, 1, 1, 0, 0, 0};
    private static final int[] STEALTH_PUFFS = {4, 19, 37, 52, 70, 88, 101, 117, 131, 149, 168, 186, 203, 221, 236, 249};
    private static final int[] RIFT_ZIGZAG = {0, 1, 1, 0, -1, 0, 1, 0, 0, -1};
    private static final int[] AXES_CHIPS = {27, 30, 41};
    private static final int[] AXES_SPRIG_OFFSETS = {-5, 5};
    private static final int[] KINETICS_HOP = {0, 1, 2, 1};
    private static final int[] ARCHITECT_SWING = {0, 1, 0, 0};
    private static final double[] CRAFTING_PLANK_START = {0.0, 0.13, 0.26, 0.39};
    private static final int[] ENCHANTING_SPOTS = {10, 34, 61, 85, 118, 140, 166, 192, 214, 245};
    private static final String[][] ENCHANTING_GLYPHS = {
            {"g0p", "g03", "g01"},
            {"g1p", "g13", "g11"},
            {"g2p", "g23", "g21"},
            {"g3p", "g33", "g31"},
            {"g4p", "g43", "g41"}
    };
    private static final int[] GOLEMANCY_NOD = {0, 0, 1, 0};

    private HoverFrames() {
    }

    static void draw(String kit, FrameCanvas F) {
        switch (kit) {
            case "herbalism" -> herbalism(F);
            case "seaborne" -> seaborne(F);
            case "stealth" -> stealth(F);
            case "nether" -> nether(F);
            case "chronos" -> chronos(F);
            case "rift" -> rift(F);
            case "swords" -> swords(F);
            case "axes" -> axes(F);
            case "unarmed" -> unarmed(F);
            case "ranged" -> ranged(F);
            case "blocking" -> blocking(F);
            case "hunter" -> hunter(F);
            case "tragoul" -> tragoul(F);
            case "agility" -> agility(F);
            case "kinetics" -> kinetics(F);
            case "pickaxes" -> pickaxes(F);
            case "excavation" -> excavation(F);
            case "architect" -> architect(F);
            case "crafting" -> crafting(F);
            case "enchanting" -> enchanting(F);
            case "brewing" -> brewing(F);
            case "discovery" -> discovery(F);
            case "taming" -> taming(F);
            case "golemancy" -> golemancy(F);
            default -> {
            }
        }
    }

    private static int floor(double x) {
        return (int) Math.floor(x);
    }

    private static int corner(FrameCanvas F, int ci) {
        return switch (ci) {
            case 0 -> F.TL;
            case 1 -> F.TR;
            case 2 -> F.BR;
            default -> F.BL;
        };
    }

    private static int sideLength(FrameCanvas F, int ci) {
        return (ci & 1) == 0 ? F.w : F.h;
    }

    // Two shoots climb from a bottom-left root to meet, leaves unfurl behind them, then it fruits; the fruit drops on leaving.
    private static void herbalism(FrameCanvas F) {
        int half = F.P >> 1;
        int n = floor(F.k(0, 0.68) * half);
        int sw = floor(F.t * 1.6);
        for (int dir = 1; dir >= -1; dir -= 2) {
            for (int i = 0; i < n; i++) {
                F.px(F.BL + dir * i, HERBALISM_WAVE[i & 7], i % 4 != 0 ? '2' : '1');
            }
            for (int i = 7, j = 0; i < n - 2; i += HERBALISM_LEAF_STEP[j % 5], j++) {
                int f = (j + ((sw + (i >> 4)) & 1)) & 1;
                F.spr(n - i < 6 ? "bud" : f == 1 ? "leaf1" : "leaf0", F.BL + dir * i, F.o().flip(dir < 0));
            }
            if (n < half) {
                F.spr("curl", F.BL + dir * n, F.o().flip(dir > 0));
            }
        }
        if (F.e > 0) {
            F.spr("root", F.BL - 3, F.o().up());
        }
        herbalismFruit(F, F.P - 8, 0.72, 0);
        herbalismFruit(F, F.TR + 8, 0.8, 1);
        herbalismFruit(F, F.BR + 30, 0.87, 2);
        herbalismFruit(F, F.BR + 76, 0.94, 3);
    }

    private static void herbalismFruit(FrameCanvas F, int s, double threshold, int i) {
        double a = F.k(threshold, threshold + 0.05);
        if (F.out && F.e < threshold + 0.06) {
            double d = (threshold + 0.06 - F.e) * 60;
            if (d < 4) {
                F.spr("fruit", s, F.o().up().dy(d).a(1 - d / 4));
            }
        } else if (a > 0) {
            F.spr(a < 1 ? "fr0" : "fruit", s, F.o().up().dy(F.e >= 1 && floor(F.t * 2.5) % 9 == i * 2 ? 1 : 0));
        }
    }

    // Water wells up at the bottom and climbs both sides to close over the top; it laps, bubbles rise, and it drains away.
    private static void seaborne(FrameCanvas F) {
        int half = F.P >> 1;
        int n = floor(F.k(0, 0.8) * half);
        int ph = floor(F.t * 5);
        for (int dir = 1; dir >= -1; dir -= 2) {
            for (int i = 0; i < n; i++) {
                int s = F.bot + dir * i;
                boolean wave = SEABORNE_WAVE[(i * dir + ph + 800) & 7] != 0;
                F.px(s, 0, wave ? '2' : '1');
                F.px(s, 1, wave ? '3' : '2');
                if (wave && (i + ph) % 8 == 3) {
                    F.px(s, 2, '3');
                }
            }
            if (n > 2 && n < half) {
                F.spr("drop", F.bot + dir * n);
            }
        }
        if (n >= half) {
            for (int i = 0; i < 2; i++) {
                double q = (F.t / 2.6 + i * 0.5) % 1;
                double s = i != 0 ? F.BR - 4 - q * (F.h - 8) : F.BL + 4 + q * (F.h - 8);
                F.spr("bub", s, F.o().out(2).a(Math.sin(q * Math.PI)));
            }
        }
    }

    // Smoke puffs curl in and thicken into a shadow rim; they drift and breathe, then thin and float outward.
    private static void stealth(FrameCanvas F) {
        if (F.e > 0.45) {
            for (int s = 0; s < F.P; s++) {
                F.px(s, 0, 'a', F.k(0.45, 1) * 0.85);
            }
        }
        for (int i = 0; i < STEALTH_PUFFS.length; i++) {
            double threshold = ((i * 7) % 16) / 16.0 * 0.55;
            double a = F.k(threshold, threshold + 0.4);
            if (a <= 0) {
                continue;
            }
            int T = floor(F.t * 1.3 + i * 0.7) & 3;
            double s = STEALTH_PUFFS[i] * F.P / 256.0;
            boolean tall = (F.side(s) & 1) != 0;
            F.spr(a < 0.5 ? "ps" : "pb", s, F.o()
                    .a(a * (0.85 + 0.15 * Math.sin(F.t * 1.7 + i)))
                    .sink(1)
                    .out((F.out && a < 0.6 ? 1 : 0) + (T == 1 && !tall ? 1 : 0))
                    .dx(T == 3 && !tall ? 1 : 0)
                    .dy(T == 3 && tall ? 1 : 0));
        }
    }

    // A spark runs the outline and leaves it glowing while flames catch behind it; the flames flicker, then sink to embers.
    private static void nether(FrameCanvas F) {
        int sp = floor(F.k(0, 0.42) * F.P);
        for (int i = 0; i < sp; i++) {
            F.px(F.BL + i, 0, i % 5 == 2 ? 'p' : i % 3 != 0 ? '2' : '1', F.out ? 0.4 + 0.6 * F.e : 1);
        }
        if (sp < F.P && F.e > 0) {
            F.px(F.BL + sp, 0, 'q');
            F.px(F.BL + sp, 1, 'q');
            F.px(F.BL + sp - 1, 1, 'p');
            F.px(F.BL + sp + 1, 0, 'p');
            F.px(F.BL + sp - 2, 2, 'p', 0.6);
        }
        netherFlame(F, F.TL + 6, 0);
        netherFlame(F, F.TL + 21, 1);
        netherFlame(F, F.TL + 33, 2);
        netherFlame(F, F.TL + 52, 3);
        netherFlame(F, F.TL + 64, 4);
        netherFlame(F, F.TL + 83, 5);
        netherFlame(F, F.TL + 97, 6);
        netherFlame(F, F.TR + 8, 7);
        netherFlame(F, F.TR + 16, 8);
        netherFlame(F, F.P - 8, 9);
        netherFlame(F, F.P - 16, 10);
        if (F.e > 0.5) {
            for (int i = 0; i < 7; i++) {
                F.px(F.BR + 9 + i * 14, 1, 'p', F.k(0.5, 0.8) * (0.5 + 0.5 * Math.sin(F.t * 3 + i * 1.9)));
            }
        }
        if (F.e >= 1) {
            int s = floor(F.t * 70);
            F.px(s, 1, 'q', 0.9);
            F.px(s - 1, 1, 'p', 0.6);
            F.px(s - 2, 1, 'p', 0.3);
        }
    }

    private static void netherFlame(FrameCanvas F, int s, int i) {
        double threshold = 0.42 + i * 0.05;
        double a = F.k(threshold, threshold + 0.1);
        if (a <= 0) {
            return;
        }
        int flicker = floor(F.t * 7 + i * 1.3) & 1;
        F.spr(a < 0.6 ? "fs" : flicker != 0 ? "fb" : "fa", s, F.o().up().flip((i & 1) != 0));
    }

    // A brass ring goes on tick by tick and two pinions settle onto opposite corners; they turn opposite ways and a second hand steps round.
    private static void chronos(FrameCanvas F) {
        int N = F.P / 6;
        int n = floor(F.k(0, 0.85) * N + 0.001);
        int hand = floor(F.t * 2) % N;
        for (int i = 0; i < n; i++) {
            int s = F.TL + i * 6;
            boolean hot = F.e >= 1 && i == hand;
            for (int j = 0; j < 6; j++) {
                F.px(s + j, 0, j != 0 ? 'b' : 'a');
            }
            F.px(s, 1, hot ? 'p' : 'b');
            if (i % 5 == 0 || hot) {
                F.px(s, 2, hot ? 'p' : 'b');
            }
        }
        int tick = floor(F.t * 3);
        chronosGear(F, F.x0 - 4, F.y0 - 4, 0.3, Math.floorMod(tick, PINION_FRAMES), -1, 0);
        chronosGear(F, F.x1 - 2, F.y1 - 2, 0.6, Math.floorMod(-tick, PINION_FRAMES), 0, -1);
    }

    /** A pinion on the page's own gear frames, settling in from {@code (dx, dy)} while it fades in. */
    private static void chronosGear(FrameCanvas F, int x, int y, double threshold, int frame, int dx, int dy) {
        double a = F.k(threshold, threshold + 0.1);
        int settle = a < 1 ? 1 : 0;
        if (a > 0) {
            F.raw(PINION_KEYS[frame], x + dx * settle, y + dy * settle, a);
        }
    }

    // A crack tears both ways from the top-right into void behind a bright tip, shards hovering; it seals back on leaving.
    private static void rift(FrameCanvas F) {
        int half = F.P >> 1;
        int n = floor(F.k(0, 0.7) * half);
        int fl = floor(F.t * 5);
        for (int dir = 1; dir >= -1; dir -= 2) {
            for (int i = 0; i < n; i++) {
                int s = F.TR + dir * i;
                int z = RIFT_ZIGZAG[(i * 3 + (dir > 0 ? 0 : 4)) % 10];
                boolean wide = n - i > 7;
                char rim = (i + fl) % 7 < 3 ? '3' : '2';
                F.px(s, z, 'k');
                F.px(s, z + 1, wide ? 'k' : rim);
                if (wide) {
                    F.px(s, z + 2, rim);
                }
                if (wide && (i * 7 + (fl >> 2) * 5) % 23 == 0) {
                    F.px(s, z + 1, 'q');
                }
                if (i == n - 1 && n < half) {
                    F.px(s + dir, z, 'q');
                    F.px(s + dir * 2, z, '3');
                }
            }
        }
        riftShard(F, F.TL + 30, 0.75, 0);
        riftShard(F, F.BR + 20, 0.82, 1);
        riftShard(F, F.P - 9, 0.9, 2);
        riftShard(F, F.TR - 22, 0.95, 3);
    }

    private static void riftShard(FrameCanvas F, int s, double threshold, int i) {
        if (F.e > threshold) {
            F.spr("sh", s, F.o().out(1 + (floor(F.t * 1.5 + i) & 1)));
        }
    }

    // Two swords slide out along the top and bottom and pennants drop at the sides; a glint runs each blade.
    private static void swords(FrameCanvas F) {
        swordsBlade(F, F.TL + 1, F.k(0, 0.5));
        swordsBlade(F, F.BR + 1, F.k(0.25, 0.75));
        swordsPennant(F, F.left, 0.72, 0);
        swordsPennant(F, F.right, 0.84, 1);
        if (F.e > 0.72 && F.e < 0.9) {
            F.raw("clash", F.x0 - 3, F.y1, 1 - F.k(0.72, 0.9));
        }
    }

    private static void swordsBlade(FrameCanvas F, int s0, double kk) {
        if (kk <= 0) {
            return;
        }
        int L = F.w - 10;
        int n = floor(kk * L);
        int glint = floor(F.t * 45) % (L * 3);
        F.px(s0, 0, 'p');
        F.px(s0 + 1, 0, 'b');
        F.px(s0 + 2, 0, 'b');
        for (int j = -1; j < 3; j++) {
            F.px(s0 + 3, j, 'p');
        }
        for (int i = 0; i < n; i++) {
            F.px(s0 + 4 + i, 0, '2');
            if (i < n - 2) {
                F.px(s0 + 4 + i, 1, F.e >= 1 && Math.abs(i - glint) < 2 ? 'q' : '3');
            }
        }
        F.px(s0 + 4 + n, 0, '3');
    }

    private static void swordsPennant(FrameCanvas F, int s, double threshold, int i) {
        double a = F.k(threshold, threshold + 0.12);
        int f = floor(F.t * 1.4 + i) & 1;
        if (a > 0) {
            F.spr(f != 0 ? "pen1" : "pen0", s, F.o().up().dy(-Math.round((1 - a) * 2)).a(a));
        }
    }

    // Two logs roll in, an axe bites the top one and chips fly, then pine sprigs sprout at two corners and sway.
    private static void axes(FrameCanvas F) {
        axesLog(F, F.TL + 2, F.k(0, 0.4));
        axesLog(F, F.BR + 2, F.k(0.2, 0.6));
        double ax = F.k(0.62, 0.7);
        if (ax > 0) {
            F.spr("axe", F.TL + 34, F.o().out(ax < 1 ? 1 : 0).a(0.4 + 0.6 * ax));
        }
        if (F.e > 0.7 && F.e < 0.92) {
            for (int i = 0; i < AXES_CHIPS.length; i++) {
                F.spr("chip", F.TL + AXES_CHIPS[i], F.o()
                        .out(2 + Math.round(F.k(0.7, 0.92) * 2))
                        .dx((i - 1) * Math.round(F.k(0.7, 0.92) * 2))
                        .a(1 - F.k(0.7, 0.92)));
            }
        }
        axesSprigs(F, F.BL, 0.8, 0);
        axesSprigs(F, F.TR, 0.88, 1);
        if (F.e >= 1) {
            double q = (F.t / 3) % 1;
            if (q < 0.3) {
                F.raw("chip", F.x0 + 60, F.y1 + 1 + q * 8, 1 - q / 0.3);
            }
        }
    }

    private static void axesLog(FrameCanvas F, int s0, double kk) {
        int n = floor(kk * (F.w - 5));
        for (int i = 0; i < n; i++) {
            F.px(s0 + i, 0, i % 9 == 4 ? 'a' : 'b');
            F.px(s0 + i, 1, i % 13 == 7 ? 'b' : 'c');
        }
        if (n > 0) {
            F.spr("end", s0 + n, F.o().sink(1));
        }
    }

    private static void axesSprigs(FrameCanvas F, int c, double threshold, int i) {
        double a = F.k(threshold, threshold + 0.08);
        int f = floor(F.t * 1.5 + i) & 1;
        if (a <= 0) {
            return;
        }
        for (int offset : AXES_SPRIG_OFFSETS) {
            F.spr(a < 1 ? "spb" : f != 0 ? "sp1" : "sp0", c + offset, F.o().flip(offset < 0));
        }
    }

    // Corner posts go up, ropes are strung between them and a blow lands top-right; the ropes twang and dust lifts.
    private static void unarmed(FrameCanvas F) {
        double kk = F.k(0.3, 0.72);
        int tw = F.e >= 1 && F.t % 2.4 < 0.45 ? floor(F.t * 14) & 1 : 0;
        unarmedRopes(F, F.TL, F.w, 0, kk, tw);
        unarmedRopes(F, F.TR, F.h, 1, kk, tw);
        unarmedRopes(F, F.BR, F.w, 2, kk, tw);
        unarmedRopes(F, F.BL, F.h, 3, kk, tw);
        unarmedPost(F, F.x0 - 2, F.y1 - 3, 0);
        unarmedPost(F, F.x1 - 1, F.y1 - 3, 0.05);
        unarmedPost(F, F.x0 - 2, F.y0 - 2, 0.15);
        unarmedPost(F, F.x1 - 1, F.y0 - 2, 0.2);
        if (F.e > 0.82 && F.e < 0.97) {
            F.raw("hit", F.x1, F.y0 - 4, 1 - F.k(0.82, 0.97));
        }
        if (F.e >= 1) {
            double q = (F.t / 2.8) % 1;
            F.spr("dust", F.TR - 12 - q * 5, F.o().out(1).a(Math.sin(q * Math.PI) * 0.9));
        }
    }

    private static void unarmedRopes(FrameCanvas F, int c, int len, int si, double kk, int tw) {
        int n = floor(kk * len / 2);
        for (int i = 2; i < n; i++) {
            for (int end = 0; end < 2; end++) {
                int s = end == 0 ? c + i : c + len - 1 - i;
                int mid = i > len / 4.0 ? tw : 0;
                F.px(s, mid, ((i >> 1) & 1) != 0 ? 'c' : 'b');
                if ((si & 1) == 0) {
                    F.px(s, 2 + mid, 'p');
                }
            }
        }
    }

    private static void unarmedPost(FrameCanvas F, int x, int y, double threshold) {
        double a = F.k(threshold, threshold + 0.12);
        if (a > 0) {
            F.raw("post", x, y + (a < 1 ? 1 : 0), a);
        }
    }

    // Arrows thud in one at a time, a bowstring is drawn through them and a target hangs; the arrows quiver in turn.
    private static void ranged(FrameCanvas F) {
        double kk = F.k(0.66, 1);
        int half = F.P >> 1;
        int n = floor(kk * half);
        for (int dir = 1; dir >= -1; dir -= 2) {
            for (int i = 0; i < n; i++) {
                F.px(F.top + dir * i, 0, 'b');
            }
        }
        rangedArrow(F, F.TL + 18, 0);
        rangedArrow(F, F.TR + 11, 1);
        rangedArrow(F, F.BR + 30, 2);
        rangedArrow(F, F.TL + 70, 3);
        rangedArrow(F, F.P - 10, 4);
        rangedArrow(F, F.BR + 84, 5);
        if (F.e > 0.9) {
            F.raw("target", F.x1 - 1, F.y1 - 1, F.k(0.9, 1));
        }
    }

    private static void rangedArrow(FrameCanvas F, int s, int i) {
        double threshold = i * 0.11;
        double a = F.k(threshold, threshold + 0.08);
        if (a <= 0) {
            return;
        }
        double land = F.e - threshold - 0.08;
        boolean idle = F.e >= 1 && floor(F.t / 1.3) % 6 == i && F.t % 1.3 < 0.35;
        int wob = (land > 0 && land < 0.06) || idle ? ((floor(F.t * 14) & 1) != 0 ? 1 : -1) : 0;
        boolean side = (F.side(s) & 1) != 0;
        F.spr("arrow", s, F.o().out(a < 1 ? 1 : 0).a(a).dx(side ? 0 : wob).dy(side ? wob : 0));
    }

    // A wall is laid bottom, then sides, then top to a gold keystone that glints; it comes down from the top.
    private static void blocking(FrameCanvas F) {
        int nb = Math.floorDiv(F.w, 6);
        int ns = Math.floorDiv(F.h - 4, 6);
        int count = nb + 2 * ns + 2 * (nb >> 1);
        int n = floor(F.k(0, 0.9) * count + 0.001);
        for (int i = 0; i < n; i++) {
            F.spr("blk", blockingSlot(F, i, nb, ns), F.o()
                    .sink(1)
                    .out(i == n - 1 && F.e < 0.9 ? 1 : 0)
                    .a(F.out && i >= n - 2 ? 0.5 : 1));
        }
        if (F.e > 0.9) {
            F.spr("key", F.top, F.o().sink(1).a(F.k(0.9, 0.96)));
            if (F.e >= 1 && floor(F.t * 2) % 5 == 0) {
                F.px(F.top, 2, 'q');
            }
        }
    }

    private static int blockingSlot(FrameCanvas F, int index, int nb, int ns) {
        if (index < nb) {
            return F.BL - 3 - index * 6;
        }
        int r = index - nb;
        if (r < 2 * ns) {
            int i = r >> 1;
            return (r & 1) == 0 ? F.BL + 5 + i * 6 : F.BR - 5 - i * 6;
        }
        r -= 2 * ns;
        int i = r >> 1;
        return (r & 1) == 0 ? F.TL + 3 + i * 6 : F.TR - 4 - i * 6;
    }

    // A rope is thrown round from the bottom-right, falls slack, pulls tight and has feathers tied on that turn.
    private static void hunter(FrameCanvas F) {
        int n = floor(F.k(0, 0.4) * F.P);
        int slack = F.e < 0.4 ? 2 : F.e < 0.5 ? 1 : 0;
        for (int i = 0; i < n; i++) {
            F.px(F.BR + i, slack + (slack != 0 && i % 16 < 8 ? 0 : slack != 0 ? 1 : 0) - (slack != 0 ? 1 : 0),
                    ((i >> 1) & 1) != 0 ? 'c' : 'b');
        }
        if (n > 0) {
            F.raw("knot", F.x1 - 1, F.y1 - 1);
        }
        hunterFeather(F, F.TL + 12, 0.6, 0);
        hunterFeather(F, F.TL + 70, 0.72, 1);
        hunterFeather(F, F.P - 9, 0.84, 2);
        hunterFeather(F, F.BR + 40, 0.92, 3);
    }

    private static void hunterFeather(FrameCanvas F, int s, double threshold, int i) {
        double a = F.k(threshold, threshold + 0.06);
        int f = floor(F.t * 1.5 + i * 0.6) & 1;
        if (a > 0) {
            F.spr(a < 1 ? "f0" : f != 0 ? "fe1" : "fe0", s);
        }
    }

    // Blood wells along the top, runs down both sides behind a bulb, pools at the bottom in drips; it dries dark on leaving.
    private static void tragoul(FrameCanvas F) {
        char col = F.out ? '1' : 'p';
        int a1 = floor(F.k(0, 0.35) * (F.w >> 1));
        int a2 = floor(F.k(0.35, 0.7) * F.h);
        int a3 = floor(F.k(0.7, 0.9) * (F.w >> 1));
        int hl = floor(F.t * 8);
        for (int dir = 1; dir >= -1; dir -= 2) {
            for (int i = 0; i < a1; i++) {
                int s = F.top + dir * i;
                F.px(s, 0, col);
                if ((i * 5) % 7 < 4) {
                    F.px(s, 1, F.e >= 1 && ((i * dir - hl) % 41 + 41) % 41 < 2 ? 'q' : col);
                }
            }
            int c = dir > 0 ? F.TR : F.TL;
            int d0 = dir > 0 ? F.BR : F.BL;
            for (int i = 0; i < a2; i++) {
                F.px(c + dir * i, 0, col);
                if (i % 6 < 2) {
                    F.px(c + dir * i, 1, col);
                }
            }
            if (a2 > 1 && a2 < F.h) {
                F.spr("bulb", c + dir * a2, F.o().up());
            }
            for (int i = 0; i < a3; i++) {
                F.px(d0 + dir * i, 0, col);
                if (i % 9 < 3) {
                    F.px(d0 + dir * i, 1, col);
                }
            }
        }
        tragoulDrip(F, F.BR + 22, 0, col);
        tragoulDrip(F, F.BR + 57, 1, col);
        tragoulDrip(F, F.BR + 88, 2, col);
    }

    private static void tragoulDrip(FrameCanvas F, int s, int i, char col) {
        double a = F.k(0.9 + i * 0.02, 0.96 + i * 0.02);
        if (a <= 0) {
            return;
        }
        double q = (F.t / 2.4 + i / 3.0) % 1;
        boolean go = F.e >= 1 && q > 0.75;
        F.spr(a < 1 || go ? "d0" : "d1", s, F.o().up());
        if (go) {
            F.px(s, 3, col, 1 - (q - 0.75) * 4);
        }
    }

    // A gust whips round the tile leaving streaming dashes and a fluttering feather; on leaving the dashes blow off.
    private static void agility(FrameCanvas F) {
        double head = F.k(0, 0.62) * F.P * 1.5;
        int sc = floor(F.t * (F.out ? 45 : 14));
        double a = F.out ? F.e : 1;
        int s0 = F.left;
        for (int i = 0; i < Math.min(F.P, head); i++) {
            int d = ((i - sc) % 9 + 9) % 9;
            if (d < 5) {
                F.px(s0 + i, d == 4 ? 1 : 0, d > 2 ? '3' : '2', a);
            }
        }
        if (F.e < 0.62 && !F.out && F.e > 0) {
            for (int j = 0; j < 16; j++) {
                F.px(s0 + head - j, 1, j < 4 ? '3' : '2', 1 - j / 16.0);
                if (j < 3) {
                    F.px(s0 + head - j, 2, '3');
                }
            }
        }
        if (F.e > 0.8) {
            int f = floor(F.t * 3) & 1;
            F.spr(f != 0 ? "fe1" : "fe0", F.TR - 5, F.o().a(F.k(0.8, 0.9)));
        }
    }

    // Rock slabs slam in, debris flies off the corners and glowing cracks open and pulse; the slabs fly apart on leaving.
    private static void kinetics(FrameCanvas F) {
        double k0 = F.out ? F.e : F.k(0, 0.3);
        int off = (int) Math.round((1 - k0) * 3);
        double a = 0.3 + 0.7 * k0;
        for (int s = 0; s < F.P; s++) {
            F.px(s, off, s % 11 == 5 ? 'a' : 'b', a);
            if (off < 3) {
                F.px(s, off + 1, s % 7 == 3 ? 'b' : 'c', a);
            }
        }
        if (!F.out && F.e > 0.3) {
            double g = F.k(0.3, 0.55);
            double hot = F.e >= 1 ? 0.6 + 0.4 * Math.sin(F.t * 3) : g;
            kineticsCrack(F, F.TL + 15, 0, g, hot);
            kineticsCrack(F, F.TL + 48, 1, g, hot);
            kineticsCrack(F, F.TL + 81, 2, g, hot);
            kineticsCrack(F, F.TR + 11, 3, g, hot);
            kineticsCrack(F, F.BR + 26, 4, g, hot);
            kineticsCrack(F, F.BR + 68, 5, g, hot);
            kineticsCrack(F, F.P - 12, 6, g, hot);
            if (g < 1) {
                kineticsDebris(F, F.x0 - 1, F.y0 - 1, -1, -1, g);
                kineticsDebris(F, F.x1 + 1, F.y0 - 1, 1, -1, g);
                kineticsDebris(F, F.x1 + 1, F.y1 + 1, 1, 1, g);
                kineticsDebris(F, F.x0 - 1, F.y1 + 1, -1, 1, g);
            }
        }
        if (F.e >= 1) {
            double q = F.t % 3;
            int hop = q < 0.4 ? KINETICS_HOP[floor(q * 10) & 3] : 0;
            F.raw("peb", F.x0 + 70, F.y1 + 2 - hop);
        }
    }

    private static void kineticsCrack(FrameCanvas F, int s, int i, double g, double hot) {
        F.spr((i & 1) != 0 ? "cr1" : "cr0", s, F.o().sink(1).a(i % 3 == 0 ? hot : g));
    }

    private static void kineticsDebris(FrameCanvas F, int x, int y, int dx, int dy, double g) {
        F.raw("deb", x + dx * Math.round(g * 3), y + dy * Math.round(g * 2), 1 - g);
    }

    // A rock crust creeps out from the corners and crystals push through in three steps; a glint hops between them.
    private static void pickaxes(FrameCanvas F) {
        double kk = F.k(0, 0.5);
        for (int ci = 0; ci < 4; ci++) {
            int c = corner(F, ci);
            for (int dir = 1; dir >= -1; dir -= 2) {
                int len = (int) Math.ceil(sideLength(F, dir > 0 ? ci : (ci + 3) % 4) / 2.0 * kk);
                for (int i = 0; i < len; i++) {
                    int s = c + dir * i - (dir < 0 ? 1 : 0);
                    F.px(s, 0, i % 4 == 1 ? '1' : '2');
                    if ((i * 7) % 11 < 3) {
                        F.px(s, 1, '2');
                    }
                }
            }
        }
        int gi = floor(F.t * 4) % 14;
        pickaxesCrystal(F, F.TL + 14, 0, gi);
        pickaxesCrystal(F, F.TL + 41, 1, gi);
        pickaxesCrystal(F, F.TL + 59, 2, gi);
        pickaxesCrystal(F, F.TL + 90, 3, gi);
        pickaxesCrystal(F, F.TR + 12, 4, gi);
        pickaxesCrystal(F, F.BR + 18, 5, gi);
        pickaxesCrystal(F, F.BR + 52, 6, gi);
        pickaxesCrystal(F, F.BR + 81, 7, gi);
        pickaxesCrystal(F, F.P - 11, 8, gi);
    }

    private static void pickaxesCrystal(FrameCanvas F, int s, int i, int gi) {
        double threshold = 0.3 + ((i * 4) % 9) * 0.07;
        double a = F.k(threshold, threshold + 0.12);
        if (a <= 0) {
            return;
        }
        F.spr(a < 0.4 ? "c1" : a < 0.8 ? "c2" : "c3", s);
        if (F.e >= 1 && gi == i) {
            F.pixel(s, 3, 0xFFFFFFFF, 1.0);
        }
    }

    // Corner pegs and a survey string go up, spoil heaps under the tile and two finds are brushed clean on top.
    private static void excavation(FrameCanvas F) {
        int n = floor(F.k(0.2, 0.5) * F.P);
        for (int i = 0; i < n; i++) {
            if (((i >> 1) & 1) != 0) {
                F.px(F.TL + i, 0, 'b');
            }
        }
        excavationPeg(F, F.x0 - 2, F.y0 - 2, 0);
        excavationPeg(F, F.x1 - 1, F.y0 - 2, 0.06);
        excavationPeg(F, F.x1 - 1, F.y1 - 1, 0.12);
        excavationPeg(F, F.x0 - 2, F.y1 - 1, 0.18);
        excavationMound(F, F.BR + 20, 0.5);
        excavationMound(F, F.BR + 55, 0.6);
        excavationMound(F, F.BR + 86, 0.7);
        excavationFind(F, F.TL + 30, 0);
        excavationFind(F, F.TL + 76, 1);
        if (F.e >= 1) {
            double q = (F.t / 2.6) % 1;
            if (q < 0.4) {
                F.raw("crumb", F.x1 - 58 + q * 6, F.y1 + 1 + q * 5, 1 - q / 0.4);
            }
        }
    }

    private static void excavationPeg(FrameCanvas F, int x, int y, double threshold) {
        if (F.e > threshold) {
            F.raw("peg", x, y);
        }
    }

    private static void excavationMound(FrameCanvas F, int s, double threshold) {
        double a = F.k(threshold, threshold + 0.1);
        if (a > 0) {
            F.spr(a < 1 ? "m0" : "m1", s, F.o().up());
        }
    }

    private static void excavationFind(FrameCanvas F, int s, int i) {
        double a = F.k(0.8 + i * 0.08, 0.92 + i * 0.08);
        if (F.e > 0.78) {
            F.spr("bd", s, F.o().out(1));
            F.spr("bl", s, F.o().out(1).a(a));
        }
    }

    // A dashed plan is drawn with corner ticks, bricks are laid over it and a plumb bob hangs and swings.
    private static void architect(FrameCanvas F) {
        int n1 = floor(F.k(0, 0.5) * F.P);
        int N = F.P / 6;
        int n2 = floor(F.k(0.5, 0.9) * N + 0.001);
        for (int i = 0; i < n1; i++) {
            if (((i >> 1) & 1) != 0) {
                F.px(F.TL + i, 1, '2');
            }
        }
        for (int ci = 0; ci < 4; ci++) {
            int c = corner(F, ci);
            if (n1 > c && n2 < N) {
                F.px(c, 2, '2');
                F.px(c, 3, '2');
            }
        }
        if (n1 < F.P && F.e > 0) {
            F.px(F.TL + n1, 1, 'p');
            F.px(F.TL + n1, 2, 'p');
        }
        for (int i = 0; i < n2; i++) {
            F.spr(i % 3 == 1 ? "brickb" : "brick", F.BL + 3 + i * 6, F.o().sink(1));
        }
        if (F.e > 0.9) {
            F.spr("bob", F.TR + 5, F.o().up().a(F.k(0.9, 0.96))
                    .dx(F.e >= 1 ? ARCHITECT_SWING[floor(F.t * 2) & 3] : 0));
        }
    }

    // Planks slide in from each corner and are nailed with a tap and a spark; shavings curl off and rock.
    private static void crafting(FrameCanvas F) {
        for (int ci = 0; ci < 4; ci++) {
            int c = corner(F, ci);
            int len = sideLength(F, ci);
            double start = CRAFTING_PLANK_START[ci];
            int n = floor(F.k(start, start + 0.25) * len);
            for (int i = 0; i < n; i++) {
                F.px(c + i, 0, i % 10 == 6 ? '1' : '2');
                F.px(c + i, 1, i % 14 == 3 ? '2' : '3');
            }
            if (n > 0 && n < len) {
                F.px(c + n, 0, 'k');
                F.px(c + n, 1, 'k');
            }
        }
        craftingNail(F, F.x0 - 1, F.y0 - 1, -1, -1, 0.64);
        craftingNail(F, F.x1, F.y0 - 1, 1, -1, 0.71);
        craftingNail(F, F.x1, F.y1, 1, 1, 0.78);
        craftingNail(F, F.x0 - 1, F.y1, -1, 1, 0.85);
        if (F.e > 0.9) {
            craftingShaving(F, F.TL + 45, 0);
            craftingShaving(F, F.BR + 30, 1);
        }
    }

    private static void craftingNail(FrameCanvas F, int x, int y, int dx, int dy, double threshold) {
        double a = F.k(threshold, threshold + 0.06);
        if (a <= 0) {
            return;
        }
        int o = a < 0.6 ? 2 : a < 1 ? 1 : 0;
        F.raw("nail", x + dx * o, y + dy * o);
        if (a > 0.6 && a < 1) {
            F.raw("tap", x + dx * 3, y + dy * 2);
        }
    }

    private static void craftingShaving(FrameCanvas F, int s, int phase) {
        F.spr("shav", s, F.o().out(2).flip(((floor(F.t * 1.2) + phase) & 1) != 0).a(F.k(0.9, 1)));
    }

    // A thread of light is drawn round and runes are written on it, pale then dark; a gold glow passes rune to rune.
    private static void enchanting(FrameCanvas F) {
        int chase = floor(F.t * 3) % ENCHANTING_SPOTS.length;
        int thread = floor(F.k(0, 0.8) * F.P);
        for (int i = 0; i < thread; i += 2) {
            F.px(F.TL + i, 0, '3');
        }
        for (int i = 0; i < ENCHANTING_SPOTS.length; i++) {
            double threshold = i * 0.08;
            double a = F.k(threshold, threshold + 0.1);
            if (a <= 0) {
                continue;
            }
            int role = F.e >= 1 && i == chase ? 0 : a < 1 ? 1 : 2;
            F.spr(ENCHANTING_GLYPHS[i % 5][role], ENCHANTING_SPOTS[i] * F.P / 256.0,
                    F.o().out(F.e >= 1 ? floor(F.t * 1.2 + i * 0.5) & 1 : 0));
        }
    }

    // Glass pipe is fitted from the corners and the brew runs through it; bubbles travel and a drip falls bottom-right.
    private static void brewing(FrameCanvas F) {
        double kp = F.k(0, 0.4);
        int fill = floor(F.k(0.4, 0.9) * F.P);
        int bub = floor(F.t * 30);
        for (int ci = 0; ci < 4; ci++) {
            int c = corner(F, ci);
            for (int dir = 1; dir >= -1; dir -= 2) {
                int len = (int) Math.ceil(sideLength(F, dir > 0 ? ci : (ci + 3) % 4) / 2.0 * kp);
                for (int i = 0; i < len; i++) {
                    int s = c + dir * i - (dir < 0 ? 1 : 0);
                    int d = ((s - F.TL) % F.P + F.P) % F.P;
                    F.px(s, 0, 'b');
                    F.px(s, 2, 'b');
                    F.px(s, 1, d < fill ? (F.e >= 1 && ((d - bub) % 43 + 43) % 43 == 0 ? 'q' : 'p') : 'c');
                }
            }
        }
        if (fill > 0 && fill < F.P) {
            F.px(F.TL + fill, 1, 'q');
        }
        if (F.e >= 1) {
            double q = (F.t / 2.2) % 1;
            F.spr(q < 0.5 ? "dr0" : "dr1", F.BR + 8, F.o().up().dy(2 + (q > 0.8 ? 1 : 0)).a(q > 0.8 ? 0.5 : 1));
        }
    }

    // A flag is planted and a route is dotted round to a red cross; a marker walks it and it is taken up from the start.
    private static void discovery(FrameCanvas F) {
        int N = Math.floorDiv(F.P - 14, 5);
        int n = floor(F.k(0.1, 0.85) * N + 0.001);
        int first = F.out ? N - n : 0;
        int last = F.out ? N : n;
        int walk = floor(F.t * 6) % N;
        for (int i = first; i < last; i++) {
            for (int j = 0; j < 3; j++) {
                F.px(F.TL + 7 + i * 5 + j, 0, F.e >= 1 && i == walk ? 'p' : '1');
            }
        }
        if (!F.out && n < N && n > 0) {
            F.px(F.TL + 7 + n * 5, 1, 'p');
        }
        double fa = F.k(0, 0.1);
        int f = floor(F.t * 2.5) & 1;
        if (fa > 0) {
            F.raw(f != 0 ? "flag1" : "flag0", F.x0 - 1, F.y0 - 4 + (fa < 1 ? 1 : 0), fa);
        }
        if (F.e > 0.5) {
            F.raw("rose", F.x1, F.y0 - 3, F.k(0.5, 0.6));
        }
        if (F.e > 0.88) {
            F.spr("x", F.P - 5, F.o().out(1).a(F.e >= 1 ? 0.7 + 0.3 * Math.sin(F.t * 4) : F.k(0.88, 0.94)));
        }
    }

    // Paw prints walk round from the bottom-left to a dropped bone; the walk repeats in red and the oldest prints fade first.
    private static void taming(FrameCanvas F) {
        int N = Math.floorDiv(F.P - 10, 11);
        int n = floor(F.k(0, 0.85) * N + 0.001);
        int first = F.out ? N - n : 0;
        int last = F.out ? N : n;
        int step = floor(F.t * 4) % N;
        for (int i = first; i < last; i++) {
            boolean hot = (F.e >= 1 && i == step) || (!F.out && i == n - 1 && F.e < 0.85);
            F.spr(hot ? "pawh" : "paw", F.BL + 7 + i * 11, F.o().out(i & 1));
        }
        if (F.e > 0.9) {
            F.spr("bone", F.BR + 50, F.o().up().dy(1).dx(F.e >= 1 ? floor(F.t * 1.5) & 1 : 0).a(F.k(0.9, 0.96)));
        }
    }

    // Iron plates clank on with a bounce, rivets go in, a golem head sets on the corner and ivy takes root with a poppy.
    private static void golemancy(FrameCanvas F) {
        golemancyPlate(F, F.TL, F.w, 0);
        golemancyPlate(F, F.BR, F.w, 0.1);
        golemancyPlate(F, F.BL, F.h, 0.2);
        golemancyPlate(F, F.TR, F.h, 0.3);
        int N = F.P / 13;
        int n = floor(F.k(0.5, 0.85) * N + 0.001);
        for (int i = 0; i < n; i++) {
            F.px(6 + i * 13, 0, 'a');
            F.px(6 + i * 13, 1, 'k');
        }
        if (F.e > 0.6) {
            F.raw(Math.sin(F.t * 2.5) > 0 || F.e < 1 ? "head" : "headd", F.x1 - 1, F.y0 - 4, F.k(0.6, 0.7));
        }
        if (F.e > 0.85) {
            int f = floor(F.t * 1.5) & 1;
            F.spr(f != 0 ? "ivy1" : "ivy0", F.BL + 5);
            F.spr("ivy1", F.BL - 6);
        }
        if (F.e > 0.95) {
            F.spr("poppy", F.BL - 15, F.o().out(F.e >= 1 ? GOLEMANCY_NOD[floor(F.t * 2) & 3] : 0));
        }
    }

    private static void golemancyPlate(FrameCanvas F, int c, int len, double threshold) {
        double a = F.out ? F.k(threshold * 0.6, threshold * 0.6 + 0.3) : F.k(threshold, threshold + 0.2);
        if (a <= 0) {
            return;
        }
        int off = a < 0.5 ? 2 : a < 0.8 ? 0 : a < 0.9 ? 1 : 0;
        double al = a < 0.5 ? 0.6 : 1;
        for (int i = 1; i < len - 1; i++) {
            F.px(c + i, off, 'b', al);
            F.px(c + i, off + 1, 'c', al);
        }
        F.px(c, off, 'k', al);
        F.px(c + len - 1, off, 'k', al);
    }
}
