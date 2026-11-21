package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiTexture;

import java.util.ArrayList;
import java.util.List;

/**
 * A recording {@link UiDraw} for kit tests: 6 units per visible character (colour codes take no width, as in the
 * Minecraft font), 9 units per line.
 */
public final class KitDraw implements UiDraw {
    private final List<String> calls = new ArrayList<>();
    private final int width;
    private final int height;
    private final double scale;
    private int scissors;
    private UiSprites sprites = UiSprites.NONE;

    public KitDraw(int width, int height, double scale) {
        this.width = width;
        this.height = height;
        this.scale = scale;
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
        return LegacyText.strip(text).length() * 6;
    }

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
        return Math.max(1, (textWidth(text) + maxWidth - 1) / Math.max(1, maxWidth)) * lineHeight();
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
