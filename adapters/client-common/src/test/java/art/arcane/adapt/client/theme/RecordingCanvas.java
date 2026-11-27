package art.arcane.adapt.client.theme;

import art.arcane.adapt.api.client.FrameCanvas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A {@link FrameCanvas} round a 40 by 20 tile at (100, 50) that writes down every drawing call as text. */
final class RecordingCanvas implements FrameCanvas {
    static final int WIDTH = 40;
    static final int HEIGHT = 20;
    static final int X = 100;
    static final int Y = 50;

    private final List<String> calls = new ArrayList<>();
    private final Options options = new Options();
    double assembled;
    double seconds;
    boolean leaving;

    RecordingCanvas at(double lift, double clock, boolean out) {
        assembled = lift;
        seconds = clock;
        leaving = out;
        calls.clear();
        return this;
    }

    List<String> calls() {
        return calls;
    }

    List<String> calls(String kind) {
        List<String> matching = new ArrayList<>();
        for (String call : calls) {
            if (call.startsWith(kind + " ")) {
                matching.add(call);
            }
        }
        return matching;
    }

    private static final class Options implements SpriteOptions {
        private final StringBuilder text = new StringBuilder();

        private Options reset() {
            text.setLength(0);
            return this;
        }

        private Options note(String name, Object value) {
            text.append(' ').append(name).append('=').append(value);
            return this;
        }

        @Override
        public SpriteOptions flip(boolean flipped) {
            return flipped ? note("flip", true) : this;
        }

        @Override
        public SpriteOptions upright() {
            return note("upright", true);
        }

        @Override
        public SpriteOptions sink(int pixels) {
            return pixels != 0 ? note("sink", pixels) : this;
        }

        @Override
        public SpriteOptions outward(double pixels) {
            return pixels != 0.0 ? note("out", format(pixels)) : this;
        }

        @Override
        public SpriteOptions dx(double pixels) {
            return pixels != 0.0 ? note("dx", format(pixels)) : this;
        }

        @Override
        public SpriteOptions dy(double pixels) {
            return pixels != 0.0 ? note("dy", format(pixels)) : this;
        }

        @Override
        public SpriteOptions alpha(double alpha) {
            return note("a", format(alpha));
        }

        @Override
        public SpriteOptions tint(int rgb) {
            return (rgb & 0xFFFFFF) != 0xFFFFFF ? note("tint", Integer.toHexString(rgb & 0xFFFFFF)) : this;
        }
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    @Override
    public int ringLength() {
        return 2 * (WIDTH + HEIGHT);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public int topLeft() {
        return 0;
    }

    @Override
    public int topRight() {
        return WIDTH;
    }

    @Override
    public int bottomRight() {
        return WIDTH + HEIGHT;
    }

    @Override
    public int bottomLeft() {
        return 2 * WIDTH + HEIGHT;
    }

    @Override
    public int topMiddle() {
        return WIDTH / 2;
    }

    @Override
    public int rightMiddle() {
        return WIDTH + HEIGHT / 2;
    }

    @Override
    public int bottomMiddle() {
        return WIDTH + HEIGHT + WIDTH / 2;
    }

    @Override
    public int leftMiddle() {
        return 2 * WIDTH + HEIGHT + HEIGHT / 2;
    }

    @Override
    public int left() {
        return X;
    }

    @Override
    public int top() {
        return Y;
    }

    @Override
    public int right() {
        return X + WIDTH - 1;
    }

    @Override
    public int bottom() {
        return Y + HEIGHT - 1;
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

    @Override
    public int side(double s) {
        int position = Math.floorMod((int) Math.floor(s), ringLength());
        return position < WIDTH ? 0 : position < WIDTH + HEIGHT ? 1 : position < 2 * WIDTH + HEIGHT ? 2 : 3;
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
        calls.add("pixel " + Math.floorMod((int) Math.floor(s), ringLength()) + " off=" + offset + " role=" + role + " a=" + format(alpha));
    }

    @Override
    public void pixelArgb(double s, int offset, int argb, double alpha) {
        calls.add("pixel " + Math.floorMod((int) Math.floor(s), ringLength()) + " off=" + offset + " argb=" + Integer.toHexString(argb)
                + " a=" + format(alpha));
    }

    @Override
    public SpriteOptions options() {
        return options.reset();
    }

    @Override
    public void sprite(String key, double s) {
        sprite(key, s, options());
    }

    @Override
    public void sprite(String key, double s, SpriteOptions placement) {
        calls.add("sprite " + key + " " + Math.floorMod((int) Math.floor(s), ringLength()) + ((Options) placement).text);
    }

    @Override
    public void raw(String key, double x, double y) {
        raw(key, x, y, 1.0);
    }

    @Override
    public void raw(String key, double x, double y, double alpha) {
        raw(key, x, y, alpha, 0xFFFFFF);
    }

    @Override
    public void raw(String key, double x, double y, double alpha, int rgb) {
        calls.add("raw " + key + " " + format(x) + "," + format(y) + " a=" + format(alpha) + " tint=" + Integer.toHexString(rgb & 0xFFFFFF));
    }
}
