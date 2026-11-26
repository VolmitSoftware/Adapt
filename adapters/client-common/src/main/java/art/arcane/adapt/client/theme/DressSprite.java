package art.arcane.adapt.client.theme;

/**
 * One drawn dressing sprite: its size, how many animation frames it has, which of its pixels are opaque (frame 0,
 * unmirrored) and where each frame sits in the atlas, unmirrored and mirrored. A self-timed sprite (clockwork) also
 * carries its frame rate, its frame order, whether its copies turn in linked directions and how far each copy starts
 * from the last; {@code fps} 0 means the slot's own motion steps it.
 */
public final class DressSprite {
    private final int width;
    private final int height;
    private final boolean[] mask;
    private final ThemeSprite[] frames;
    private final double fps;
    private final int[] sequence;
    private final boolean turn;
    private final int step;

    DressSprite(int width, int height, boolean[] mask, ThemeSprite[] frames, double fps, int[] sequence, boolean turn, int step) {
        this.width = width;
        this.height = height;
        this.mask = mask;
        this.frames = frames;
        this.fps = fps;
        this.sequence = sequence;
        this.turn = turn;
        this.step = step;
    }

    /** Frames a second for a self-timed sprite, or 0. */
    public double fps() {
        return fps;
    }

    /** Whether placed copies turn, alternating direction in placement order so neighbours counter-rotate. */
    public boolean turn() {
        return turn;
    }

    /** How many frames each placed copy starts on from the previous copy of its kit, or 0 for a start from its phase. */
    public int step() {
        return step;
    }

    /** The length of one loop: the frame order's length, or the frame count. */
    public int loop() {
        return sequence != null ? sequence.length : frameCount();
    }

    /**
     * The frame a self-timed copy shows at {@code seconds}: its {@code start}, stepped at the sprite's rate in
     * direction {@code direction}, read through the frame order. At 0 seconds every copy shows its own start.
     */
    public int frameAt(double seconds, int start, int direction) {
        int n = loop();
        int index = Math.floorMod(start + (int) Math.floor(seconds * fps) * direction, n);
        return sequence != null ? sequence[index] : index;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int frameCount() {
        return frames.length / 2;
    }

    /** Frame {@code frame} (wrapped), mirrored when {@code flip}. */
    public ThemeSprite frame(int frame, boolean flip) {
        int count = frameCount();
        int index = ((frame % count) + count) % count;
        return frames[index * 2 + (flip ? 1 : 0)];
    }

    /** Whether the pixel at (x, y) of frame 0 is drawn, reading a mirrored sprite from its right. */
    public boolean opaque(boolean flip, int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        return mask[y * width + (flip ? width - 1 - x : x)];
    }
}
