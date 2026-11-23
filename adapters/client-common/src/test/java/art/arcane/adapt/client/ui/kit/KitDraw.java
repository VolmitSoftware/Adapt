package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiTexture;

import java.util.ArrayList;
import java.util.List;

/**
 * A recording {@link UiDraw} for kit tests: 6 units per visible character (colour codes take no width, as in the
 * Minecraft font), 9 units per line. {@link #vanilla} measures with the vanilla font's own advances and wraps at word
 * breaks as the game does, for tests about what fits a page.
 */
public final class KitDraw implements UiDraw {
    private final List<String> calls = new ArrayList<>();
    private final int width;
    private final int height;
    private final double scale;
    private final boolean vanilla;
    private int scissors;
    private UiSprites sprites = UiSprites.NONE;

    public KitDraw(int width, int height, double scale) {
        this(width, height, scale, false);
    }

    private KitDraw(int width, int height, double scale, boolean vanilla) {
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.vanilla = vanilla;
    }

    /** A draw that measures text as the vanilla font does. */
    public static KitDraw vanilla(int width, int height, double scale) {
        return new KitDraw(width, height, scale, true);
    }

    public boolean measuresVanilla() {
        return vanilla;
    }

    public KitDraw() {
        this(427, 240, 2.0);
    }

    public List<String> calls() {
        return calls;
    }

    public List<String> calls(String prefix) {
        return calls.stream().filter((String call) -> call.startsWith(prefix)).toList();
    }

    public int openScissors() {
        return scissors;
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
    public double guiScale() {
        return scale;
    }

    @Override
    public int lineHeight() {
        return 9;
    }

    @Override
    public int textWidth(String text) {
        String plain = LegacyText.strip(text);
        if (!vanilla) {
            return plain.length() * 6;
        }
        int width = 0;
        for (int index = 0; index < plain.length(); index++) {
            char glyph = plain.charAt(index);
            width += glyph >= 32 && glyph < 127 ? ADVANCES.charAt(glyph - 32) - '0' : 6;
        }
        return width;
    }

    /** The vanilla font's advance for each printable ASCII character from the space on. */
    private static final String ADVANCES = "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";

    @Override
    public void fill(int left, int top, int right, int bottom, int color) {
        calls.add("fill " + left + "," + top + "," + right + "," + bottom + " " + Integer.toHexString(color));
    }

    @Override
    public void gradient(int left, int top, int right, int bottom, int topColor, int bottomColor) {
        calls.add("gradient " + left + "," + top + "," + right + "," + bottom);
    }

    @Override
    public void outline(int x, int y, int w, int h, int color) {
        calls.add("outline " + x + "," + y + "," + w + "," + h + " " + Integer.toHexString(color));
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
        calls.add("text " + x + "," + y + " " + text);
    }

    @Override
    public int wrappedText(String text, int x, int y, int maxWidth, int color, boolean shadow) {
        calls.add("wrapped " + x + "," + y + " " + maxWidth + " " + text);
        return wrappedHeight(text, maxWidth);
    }

    @Override
    public int wrappedHeight(String text, int maxWidth) {
        if (!vanilla) {
            return Math.max(1, (textWidth(text) + maxWidth - 1) / Math.max(1, maxWidth)) * lineHeight();
        }
        int lines = 0;
        for (String paragraph : text.split("\n", -1)) {
            lines++;
            int used = 0;
            for (String word : paragraph.split(" ", -1)) {
                int needed = textWidth(word);
                int next = used == 0 ? needed : used + textWidth(" ") + needed;
                if (used > 0 && next > maxWidth) {
                    lines++;
                    used = needed;
                } else {
                    used = next;
                }
                while (used > maxWidth && maxWidth > 0) {
                    lines++;
                    used -= maxWidth;
                }
            }
        }
        return Math.max(1, lines) * lineHeight();
    }

    @Override
    public void item(String itemId, String model, int x, int y) {
        calls.add("item " + itemId + " " + x + "," + y);
    }

    @Override
    public void item(String itemId, String model, int customModelData, boolean glint, int x, int y) {
        calls.add("item " + itemId + " cmd=" + customModelData + " glint=" + glint + " " + x + "," + y);
    }

    /** Serves {@code available} as the resource packs' textures from now on. */
    public KitDraw withSprites(UiSprites available) {
        sprites = available;
        return this;
    }

    @Override
    public UiSprites sprites() {
        return sprites;
    }

    @Override
    public void blit(UiTexture texture, int x, int y, int w, int h, int u, int v, int regionWidth, int regionHeight) {
        calls.add("blit " + texture.id() + " " + x + "," + y + " " + w + "x" + h);
    }

    @Override
    public void pushScissor(int left, int top, int right, int bottom) {
        scissors++;
        calls.add("pushScissor " + left + "," + top + "," + right + "," + bottom);
    }

    @Override
    public void popScissor() {
        scissors--;
        calls.add("popScissor");
    }

    @Override
    public void pushPose() {
        calls.add("pushPose");
    }

    @Override
    public void popPose() {
        calls.add("popPose");
    }

    @Override
    public void translate(float x, float y) {
        calls.add("translate " + x + "," + y);
    }

    @Override
    public void scale(float x, float y) {
        calls.add("scale " + x + "," + y);
    }

    @Override
    public void nextLayer() {
        calls.add("nextLayer");
    }

    @Override
    public void depth(float z) {
        calls.add("depth " + z);
    }
}
