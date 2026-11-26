package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiTexture;

import java.util.ArrayList;
import java.util.List;

/** Records every atlas blit as {x, y, width, height, u, v} and draws nothing. */
final class RecordingDraw implements UiDraw {
    final List<int[]> blits = new ArrayList<>();

    @Override
    public int width() {
        return 640;
    }

    @Override
    public int height() {
        return 360;
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
    }

    @Override
    public void gradient(int left, int top, int right, int bottom, int topColor, int bottomColor) {
    }

    @Override
    public void outline(int x, int y, int width, int height, int color) {
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
    }

    @Override
    public int wrappedText(String text, int x, int y, int maxWidth, int color, boolean shadow) {
        return 0;
    }

    @Override
    public int wrappedHeight(String text, int maxWidth) {
        return 0;
    }

    @Override
    public void item(String itemId, String model, int x, int y) {
    }

    @Override
    public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        blits.add(new int[]{x, y, width, height, u, v});
    }

    @Override
    public void pushScissor(int left, int top, int right, int bottom) {
    }

    @Override
    public void popScissor() {
    }

    @Override
    public void pushPose() {
    }

    @Override
    public void popPose() {
    }

    @Override
    public void translate(float x, float y) {
    }

    @Override
    public void scale(float x, float y) {
    }
}
