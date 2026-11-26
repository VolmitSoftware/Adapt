package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiScreen;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiTexture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test doubles: a sized draw that can record calls, a manual clock and a host with a clipboard. */
final class HudFakes {
    /** The shipped English strings. */
    static final Map<String, String> ENGLISH = english();

    private HudFakes() {
    }

    private static Map<String, String> english() {
        try (InputStream stream = HudFakes.class.getResourceAsStream("/assets/adapt/lang/en_us.json")) {
            JsonObject object = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> strings = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                strings.put(entry.getKey(), entry.getValue().getAsString());
            }
            return strings;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static final class Clock implements HudClock {
        long millis = 10_000L;
        long epoch = 1_800_000_000_000L;

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public long epochMillis() {
            return epoch;
        }

        void advance(long amount) {
            millis += amount;
            epoch += amount;
        }
    }

    static final class Host implements UiHost {
        String clipboard = "";
        UiScreen current;
        int clicks;

        @Override
        public void open(UiScreen screen) {
            current = screen;
        }

        @Override
        public void close() {
            current = null;
        }

        @Override
        public UiScreen current() {
            return current;
        }

        @Override
        public void playClick() {
            clicks++;
        }

        @Override
        public String clipboard() {
            return clipboard;
        }

        @Override
        public void setClipboard(String text) {
            clipboard = text;
        }

        @Override
        public String translate(String key) {
            return ENGLISH.getOrDefault(key, key);
        }
    }

    /**
     * Text is 6 units per character (section codes excluded) and 9 tall, like the vanilla font's common glyphs. Wrapping
     * breaks at spaces like the vanilla splitter, and between characters inside a word wider than a line.
     */
    static final class Draw implements UiDraw {
        final int width;
        final int height;
        final double guiScale;
        final boolean recording;
        final List<String> calls = new ArrayList<>();
        int pose;
        UiSprites sprites = UiSprites.NONE;

        Draw(int width, int height, double guiScale, boolean recording) {
            this.width = width;
            this.height = height;
            this.guiScale = guiScale;
            this.recording = recording;
        }

        /** The scaled GUI size vanilla uses for a framebuffer of {@code pixelWidth} x {@code pixelHeight}. */
        static Draw window(int pixelWidth, int pixelHeight, int guiScale) {
            int scale = guiScale <= 0 ? autoScale(pixelWidth, pixelHeight) : guiScale;
            return new Draw((int) Math.ceil(pixelWidth / (double) scale), (int) Math.ceil(pixelHeight / (double) scale), scale, false);
        }

        static int autoScale(int pixelWidth, int pixelHeight) {
            int scale = 1;
            while (pixelWidth / (scale + 1) >= 320 && pixelHeight / (scale + 1) >= 240) {
                scale++;
            }
            return scale;
        }

        boolean drew(String prefix) {
            for (String call : calls) {
                if (call.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        /** Text drawn on one line or wrapped, in drawing order. */
        List<String> texts() {
            List<String> texts = new ArrayList<>();
            for (String call : calls) {
                if (call.startsWith("text ") || call.startsWith("wrapped ")) {
                    int start = call.indexOf(' ') + 1;
                    texts.add(call.substring(call.indexOf(' ', call.indexOf(' ', start) + 1) + 1));
                }
            }
            return texts;
        }

        @Override
        public UiSprites sprites() {
            return sprites;
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
            return guiScale;
        }

        @Override
        public int lineHeight() {
            return 9;
        }

        @Override
        public int textWidth(String text) {
            int visible = 0;
            for (int index = 0; index < text.length(); index++) {
                if (text.charAt(index) == '\u00a7') {
                    index++;
                } else {
                    visible++;
                }
            }
            return visible * 6;
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            if (recording) {
                calls.add("fill " + left + "," + top + "," + right + "," + bottom + " " + Integer.toHexString(color));
            }
        }

        @Override
        public void gradient(int left, int top, int right, int bottom, int topColor, int bottomColor) {
            if (recording) {
                calls.add("gradient");
            }
        }

        @Override
        public void outline(int x, int y, int width, int height, int color) {
            if (recording) {
                calls.add("outline " + x + "," + y + "," + width + "," + height + " " + Integer.toHexString(color));
            }
        }

        @Override
        public void text(String text, int x, int y, int color, boolean shadow) {
            if (recording) {
                calls.add("text " + x + "," + y + " " + Integer.toHexString(color) + " " + text);
            }
        }

        @Override
        public int wrappedText(String text, int x, int y, int maxWidth, int color, boolean shadow) {
            if (recording) {
                calls.add("wrapped " + x + "," + y + "," + maxWidth + " " + Integer.toHexString(color) + " " + text);
            }
            return wrappedHeight(text, maxWidth);
        }

        @Override
        public int wrappedHeight(String text, int maxWidth) {
            int perLine = Math.max(1, maxWidth / 6);
            int lines = 1;
            int used = 0;
            int index = 0;
            while (index <= text.length()) {
                int end = text.indexOf(' ', index);
                if (end < 0) {
                    end = text.length();
                }
                int length = 0;
                for (int at = index; at < end; at++) {
                    if (text.charAt(at) == '\u00a7') {
                        at++;
                    } else {
                        length++;
                    }
                }
                if (used > 0 && used + 1 + length <= perLine) {
                    used += 1 + length;
                } else {
                    if (used > 0) {
                        lines++;
                    }
                    lines += Math.max(0, length - 1) / perLine;
                    used = length == 0 ? 0 : (length - 1) % perLine + 1;
                }
                index = end + 1;
            }
            return lines * 9;
        }

        @Override
        public void item(String itemId, String model, int x, int y) {
            if (recording) {
                calls.add("item " + itemId + " " + x + "," + y);
            }
        }

        @Override
        public void depth(float z) {
            if (recording) {
                calls.add("depth " + z);
            }
        }

        @Override
        public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
            if (recording) {
                calls.add("blit");
            }
        }

        @Override
        public void pushScissor(int left, int top, int right, int bottom) {
            if (recording) {
                calls.add("pushScissor");
            }
        }

        @Override
        public void popScissor() {
            if (recording) {
                calls.add("popScissor");
            }
        }

        @Override
        public void nextLayer() {
            if (recording) {
                calls.add("nextLayer");
            }
        }

        @Override
        public void pushPose() {
            pose++;
            if (recording) {
                calls.add("pushPose");
            }
        }

        @Override
        public void popPose() {
            pose--;
            if (recording) {
                calls.add("popPose");
            }
        }

        @Override
        public void translate(float x, float y) {
            if (recording) {
                calls.add("translate " + x + "," + y);
            }
        }

        @Override
        public void scale(float x, float y) {
            if (recording) {
                calls.add("scale " + x + "," + y);
            }
        }
    }
}
