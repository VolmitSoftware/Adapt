package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiDraw;

/**
 * What a skill's hover frame draws with, the demo's {@code mkF}: a ring round the tile where {@code s} runs clockwise
 * along the tile's outline from its top-left corner and {@code off} is how far outside the outline a pixel sits (-1 is
 * one unit inside); single pixels in the kit's role colours, the kit's frame sprites turned to face out from the side
 * they sit on, and sprites at fixed places. {@code e} (0 to 1) is how far the frame has assembled, {@code t} the idle
 * clock in seconds and {@code out} whether it is leaving. One canvas is reused for every frame drawn, so drawing
 * allocates nothing.
 */
public final class FrameCanvas {
    /** Corner and side-middle positions on the ring. */
    public int TL;
    public int TR;
    public int BR;
    public int BL;
    public int top;
    public int right;
    public int bot;
    public int left;
    /** Ring length, tile size, and the tile's first and last pixel columns and rows. */
    public int P;
    public int w;
    public int h;
    public int x0;
    public int y0;
    public int x1;
    public int y1;
    public double e;
    public double t;
    public boolean out;

    private final Options options = new Options();
    private UiDraw draw;
    private ThemeKit kit;
    private int clipFrom;
    private int clipTo;
    private int atX;
    private int atY;
    private int atSide;
    private int atNx;
    private int atNy;

    /** Sprite placement options, reset by {@link #o()} before each use; mirrors the demo's option object. */
    public static final class Options {
        private boolean flip;
        private boolean up;
        private int sink;
        private double outward;
        private double dx;
        private double dy;
        private double alpha;

        private Options reset() {
            flip = false;
            up = false;
            sink = 0;
            outward = 0.0;
            dx = 0.0;
            dy = 0.0;
            alpha = 1.0;
            return this;
        }

        public Options flip(boolean value) {
            flip = value;
            return this;
        }

        /** Keeps the sprite upright on every side instead of turning it outward. */
        public Options up() {
            up = true;
            return this;
        }

        public Options sink(int value) {
            sink = value;
            return this;
        }

        /** Moves the sprite this far outward along the side's normal. */
        public Options out(double value) {
            outward = value;
            return this;
        }

        public Options dx(double value) {
            dx = value;
            return this;
        }

        public Options dy(double value) {
            dy = value;
            return this;
        }

        public Options a(double value) {
            alpha = value;
            return this;
        }
    }

    /**
     * Points the canvas at a tile of {@code width} by {@code height} at (x, y) and the kit, frame state and clip: only
     * ring positions whose x is at least {@code from} and below {@code to} draw.
     */
    void begin(UiDraw target, ThemeKit frameKit, int x, int y, int width, int height, double assembled, double seconds,
               boolean leaving, int from, int to) {
        draw = target;
        kit = frameKit;
        w = width;
        h = height;
        P = 2 * (width + height);
        x0 = x;
        y0 = y;
        x1 = x + width - 1;
        y1 = y + height - 1;
        TL = 0;
        TR = width;
        BR = width + height;
        BL = 2 * width + height;
        top = width >> 1;
        right = width + (height >> 1);
        bot = width + height + (width >> 1);
        left = 2 * width + height + (height >> 1);
        e = assembled;
        t = seconds;
        out = leaving;
        clipFrom = from;
        clipTo = to;
    }

    private boolean ok(double x) {
        return x >= clipFrom && x < clipTo;
    }

    /** Sets the ring point for {@code s}: its pixel, its side and the side's outward normal. */
    private void at(double s) {
        int position = Math.floorMod((int) Math.floor(s), P);
        if (position < w) {
            atX = x0 + position;
            atY = y0;
            atSide = 0;
            atNx = 0;
            atNy = -1;
        } else if (position < w + h) {
            atX = x1;
            atY = y0 + position - w;
            atSide = 1;
            atNx = 1;
            atNy = 0;
        } else if (position < 2 * w + h) {
            atX = x1 - (position - w - h);
            atY = y1;
            atSide = 2;
            atNx = 0;
            atNy = 1;
        } else {
            atX = x0;
            atY = y1 - (position - 2 * w - h);
            atSide = 3;
            atNx = -1;
            atNy = 0;
        }
    }

    /** The side ring position {@code s} lies on: 0 top, 1 right, 2 bottom, 3 left. */
    public int side(double s) {
        at(s);
        return atSide;
    }

    /** Progress of the assembly between {@code a} and {@code z}, 0 to 1. */
    public double k(double a, double z) {
        return Math.max(0.0, Math.min(1.0, (e - a) / (z - a)));
    }

    /** One pixel in {@code role}'s colour at ring position {@code s}, {@code off} units outside the outline. */
    public void px(double s, int off, char role) {
        px(s, off, role, 1.0);
    }

    public void px(double s, int off, char role, double alpha) {
        pixel(s, off, kit.color(role), alpha);
    }

    /** One pixel in an exact ARGB colour, for the few the demo draws outside the palette. */
    public void pixel(double s, int off, int color, double alpha) {
        at(s);
        if (!ok(atX)) {
            return;
        }
        fill(atX + atNx * off, atY + atNy * off, 1, 1, color, alpha);
    }

    /** A fresh set of options for the next {@link #spr} call. */
    public Options o() {
        return options.reset();
    }

    /** Frame sprite {@code key} on the ring at {@code s} with default options. */
    public void spr(String key, double s) {
        spr(key, s, o());
    }

    /**
     * Frame sprite {@code key} on the ring at {@code s}: drawn for the top edge and turned to face outward on the other
     * three, anchored on the outline and sunk {@code sink} units into it, unless {@link Options#up} keeps it upright.
     */
    public void spr(String key, double s, Options with) {
        at(s);
        if (!ok(atX)) {
            return;
        }
        int turn = with.up ? 0 : atSide;
        ThemeSprite sprite = kit.frameSprite(key, turn, with.flip);
        if (sprite == null) {
            return;
        }
        int sw = sprite.width();
        int sh = sprite.height();
        int sink = with.sink;
        double x;
        double y;
        if (with.up) {
            if (atSide == 0) {
                x = atX - (sw >> 1);
                y = atY - sh + 1;
            } else if (atSide == 2) {
                x = atX - (sw >> 1);
                y = atY;
            } else if (atSide == 1) {
                x = atX + 1;
                y = atY - (sh >> 1);
            } else {
                x = atX - sw;
                y = atY - (sh >> 1);
            }
        } else if (turn == 0) {
            x = atX - (sw >> 1);
            y = atY - sh + 1 + sink;
        } else if (turn == 1) {
            x = atX - sink;
            y = atY - (sh >> 1);
        } else if (turn == 2) {
            x = atX - (sw >> 1);
            y = atY - sink;
        } else {
            x = atX - sw + 1 + sink;
            y = atY - (sh >> 1);
        }
        double d = with.outward;
        blit(sprite, (int) Math.round(x + atNx * d + with.dx), (int) Math.round(y + atNy * d + with.dy), with.alpha);
    }

    /** Frame sprite {@code key} as drawn, with its top left at (x, y), when the column after x is within the clip. */
    public void raw(String key, double x, double y, double alpha) {
        if (!ok(x + 1)) {
            return;
        }
        ThemeSprite sprite = kit.frameRaw(key);
        if (sprite != null) {
            blit(sprite, (int) Math.round(x), (int) Math.round(y), alpha);
        }
    }

    public void raw(String key, double x, double y) {
        raw(key, x, y, 1.0);
    }

    private void blit(ThemeSprite sprite, int x, int y, double alpha) {
        int a = alphaByte(alpha);
        if (a == 0) {
            return;
        }
        draw.blit(kit.texture(), x, y, sprite.width(), sprite.height(), sprite.u(), sprite.v(), sprite.width(), sprite.height(),
                a << 24 | 0xFFFFFF);
    }

    private void fill(int x, int y, int width, int height, int color, double alpha) {
        int a = alphaByte(alpha) * (color >>> 24) / 255;
        if (a == 0) {
            return;
        }
        draw.fill(x, y, x + width, y + height, a << 24 | color & 0xFFFFFF);
    }

    static int alphaByte(double alpha) {
        double clamped = Math.max(0.0, Math.min(1.0, alpha));
        return (int) Math.round(clamped * 255.0);
    }
}
