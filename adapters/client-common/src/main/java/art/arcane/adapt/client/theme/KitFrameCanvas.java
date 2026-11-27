package art.arcane.adapt.client.theme;

import art.arcane.adapt.api.client.FrameCanvas;
import art.arcane.adapt.client.ui.UiDraw;

/**
 * The {@link FrameCanvas} hover frames draw on, the demo's {@code mkF}: single pixels in a kit's role colours, the kit's
 * frame sprites turned to face out from the side they sit on, and sprites at fixed places, all limited to a column range so
 * a mutated tile can show two frames side by side. One canvas is reused for every frame drawn, so drawing allocates
 * nothing.
 */
final class KitFrameCanvas implements FrameCanvas {
    private static final int WHITE = 0xFFFFFF;
    private final Options options = new Options();
    private UiDraw draw;
    private ThemeKit kit;
    private int ring;
    private int width;
    private int height;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private double assembled;
    private double seconds;
    private boolean leaving;
    private int clipFrom;
    private int clipTo;
    private int atX;
    private int atY;
    private int atSide;
    private int atNx;
    private int atNy;

    /** Sprite placement options, reset by {@link #options()} before each use; mirrors the demo's option object. */
    private static final class Options implements SpriteOptions {
        private boolean flip;
        private boolean up;
        private int sink;
        private double outward;
        private double dx;
        private double dy;
        private double alpha;
        private int tint;

        private Options reset() {
            flip = false;
            up = false;
            sink = 0;
            outward = 0.0;
            dx = 0.0;
            dy = 0.0;
            alpha = 1.0;
            tint = WHITE;
            return this;
        }

        @Override
        public SpriteOptions flip(boolean flipped) {
            flip = flipped;
            return this;
        }

        @Override
        public SpriteOptions upright() {
            up = true;
            return this;
        }

        @Override
        public SpriteOptions sink(int pixels) {
            sink = pixels;
            return this;
        }

        @Override
        public SpriteOptions outward(double pixels) {
            outward = pixels;
            return this;
        }

        @Override
        public SpriteOptions dx(double pixels) {
            dx = pixels;
            return this;
        }

        @Override
        public SpriteOptions dy(double pixels) {
            dy = pixels;
            return this;
        }

        @Override
        public SpriteOptions alpha(double value) {
            alpha = value;
            return this;
        }

        @Override
        public SpriteOptions tint(int rgb) {
            tint = rgb & WHITE;
            return this;
        }
    }

    /**
     * Points the canvas at a tile of {@code tileWidth} by {@code tileHeight} at (x, y) and the kit, frame state and clip:
     * only ring positions whose x is at least {@code from} and below {@code to} draw.
     */
    void begin(UiDraw target, ThemeKit frameKit, int x, int y, int tileWidth, int tileHeight, double lift, double clock,
               boolean out, int from, int to) {
        draw = target;
        kit = frameKit;
        width = tileWidth;
        height = tileHeight;
        ring = 2 * (tileWidth + tileHeight);
        left = x;
        top = y;
        right = x + tileWidth - 1;
        bottom = y + tileHeight - 1;
        assembled = lift;
        seconds = clock;
        leaving = out;
        clipFrom = from;
        clipTo = to;
    }

    @Override
    public int ringLength() {
        return ring;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int topLeft() {
        return 0;
    }

    @Override
    public int topRight() {
        return width;
    }

    @Override
    public int bottomRight() {
        return width + height;
    }

    @Override
    public int bottomLeft() {
        return 2 * width + height;
    }

    @Override
    public int topMiddle() {
        return width >> 1;
    }

    @Override
    public int rightMiddle() {
        return width + (height >> 1);
    }

    @Override
    public int bottomMiddle() {
        return width + height + (width >> 1);
    }

    @Override
    public int leftMiddle() {
        return 2 * width + height + (height >> 1);
    }

    @Override
    public int left() {
        return left;
    }

    @Override
    public int top() {
        return top;
    }

    @Override
    public int right() {
        return right;
    }

    @Override
    public int bottom() {
        return bottom;
    }

    @Override
    public double assembled() {
        return assembled;
    }

    @Override
    public double seconds() {
        return seconds;
    }

    @Override
    public boolean leaving() {
        return leaving;
    }

    private boolean ok(double x) {
        return x >= clipFrom && x < clipTo;
    }

    /** Sets the ring point for {@code s}: its pixel, its side and the side's outward normal. */
    private void at(double s) {
        int position = Math.floorMod((int) Math.floor(s), ring);
        if (position < width) {
            atX = left + position;
            atY = top;
            atSide = 0;
            atNx = 0;
            atNy = -1;
        } else if (position < width + height) {
            atX = right;
            atY = top + position - width;
            atSide = 1;
            atNx = 1;
            atNy = 0;
        } else if (position < 2 * width + height) {
            atX = right - (position - width - height);
            atY = bottom;
            atSide = 2;
            atNx = 0;
            atNy = 1;
        } else {
            atX = left;
            atY = bottom - (position - 2 * width - height);
            atSide = 3;
            atNx = -1;
            atNy = 0;
        }
    }

    @Override
    public int side(double s) {
        at(s);
        return atSide;
    }

    @Override
    public double progress(double from, double to) {
        return Math.max(0.0, Math.min(1.0, (assembled - from) / (to - from)));
    }

    @Override
    public void pixel(double s, int offset, char role) {
        pixel(s, offset, role, 1.0);
    }

    @Override
    public void pixel(double s, int offset, char role, double alpha) {
        pixelArgb(s, offset, kit.color(role), alpha);
    }

    @Override
    public void pixelArgb(double s, int offset, int argb, double alpha) {
        at(s);
        if (!ok(atX)) {
            return;
        }
        fill(atX + atNx * offset, atY + atNy * offset, argb, alpha);
    }

    @Override
    public SpriteOptions options() {
        return options.reset();
    }

    @Override
    public void sprite(String key, double s) {
        sprite(key, s, options());
    }

    /**
     * Frame sprite {@code key} on the ring at {@code s}: drawn for the top edge and turned to face outward on the other
     * three, anchored on the outline and sunk {@code sink} units into it, unless {@link SpriteOptions#upright} keeps it
     * upright.
     */
    @Override
    public void sprite(String key, double s, SpriteOptions placement) {
        Options with = (Options) placement;
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
        blit(sprite, (int) Math.round(x + atNx * d + with.dx), (int) Math.round(y + atNy * d + with.dy), with.alpha, with.tint);
    }

    /** Frame sprite {@code key} as drawn, with its top left at (x, y), when the column after x is within the clip. */
    @Override
    public void raw(String key, double x, double y, double alpha, int rgb) {
        if (!ok(x + 1)) {
            return;
        }
        ThemeSprite sprite = kit.frameRaw(key);
        if (sprite != null) {
            blit(sprite, (int) Math.round(x), (int) Math.round(y), alpha, rgb & WHITE);
        }
    }

    @Override
    public void raw(String key, double x, double y, double alpha) {
        raw(key, x, y, alpha, WHITE);
    }

    @Override
    public void raw(String key, double x, double y) {
        raw(key, x, y, 1.0);
    }

    /** {@code sprite} with its colours multiplied by {@code rgb}. */
    private void blit(ThemeSprite sprite, int x, int y, double alpha, int rgb) {
        int a = alphaByte(alpha);
        if (a == 0) {
            return;
        }
        draw.blit(kit.texture(), x, y, sprite.width(), sprite.height(), sprite.u(), sprite.v(), sprite.width(), sprite.height(),
                a << 24 | rgb);
    }

    private void fill(int x, int y, int color, double alpha) {
        int a = alphaByte(alpha) * (color >>> 24) / 255;
        if (a == 0) {
            return;
        }
        draw.fill(x, y, x + 1, y + 1, a << 24 | color & 0xFFFFFF);
    }

    static int alphaByte(double alpha) {
        double clamped = Math.max(0.0, Math.min(1.0, alpha));
        return (int) Math.round(clamped * 255.0);
    }
}
