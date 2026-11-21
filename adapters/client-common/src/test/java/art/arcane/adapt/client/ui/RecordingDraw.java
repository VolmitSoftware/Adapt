package art.arcane.adapt.client.ui;

import java.util.ArrayList;
import java.util.List;

/** Records draw calls as text, one line each; text is 6 units per character and 9 units tall. */
public final class RecordingDraw implements UiDraw {
    private final List<String> calls = new ArrayList<>();

    public List<String> calls() {
        return calls;
    }

    @Override
    public int width() {
        return 320;
    }

    @Override
    public int height() {
        return 240;
    }

    @Override
    public double guiScale() {
        return 2.0;
    }

    @Override
    public int lineHeight() {
        return 9;
    }

    @Override
    public int textWidth(String text) {
        return text.length() * 6;
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
    public void outline(int x, int y, int width, int height, int color) {
        calls.add("outline " + x + "," + y + "," + width + "," + height);
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
        calls.add("text " + x + "," + y + " " + Integer.toHexString(color) + " " + shadow + " " + text);
    }

    @Override
    public int wrappedText(String text, int x, int y, int maxWidth, int color, boolean shadow) {
        calls.add("wrapped " + x + "," + y + " " + maxWidth + " " + text);
        return wrappedHeight(text, maxWidth);
    }

    @Override
    public int wrappedHeight(String text, int maxWidth) {
        return Math.max(1, (textWidth(text) + maxWidth - 1) / maxWidth) * lineHeight();
    }

    @Override
    public void item(String itemId, String model, int x, int y) {
        calls.add("item " + itemId + " " + model + " " + x + "," + y);
    }

    @Override
    public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        calls.add("blit " + x + "," + y + " " + width + "x" + height + " from " + u + "," + v + " " + regionWidth + "x" + regionHeight);
    }

    @Override
    public void pushScissor(int left, int top, int right, int bottom) {
        calls.add("pushScissor");
    }

    @Override
    public void popScissor() {
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
}
