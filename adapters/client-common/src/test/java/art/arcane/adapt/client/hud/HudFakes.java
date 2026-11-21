package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiScreen;
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

    /** Text is 6 units per character (section codes excluded) and 9 tall, like the vanilla font's common glyphs. */
    static final class Draw implements UiDraw {
        final int width;
        final int height;
        final double guiScale;
        final boolean recording;
        final List<String> calls = new ArrayList<>();
        int pose;

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

        List<String> texts() {
            List<String> texts = new ArrayList<>();
            for (String call : calls) {
                if (call.startsWith("text ")) {
                    texts.add(call.substring(call.indexOf(' ', call.indexOf(' ', 5) + 1) + 1));
                }
            }
            return texts;
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
                calls.add("wrapped " + text);
            }
            return wrappedHeight(text, maxWidth);
        }

        @Override
        public int wrappedHeight(String text, int maxWidth) {
            return Math.max(1, (textWidth(text) + maxWidth - 1) / Math.max(1, maxWidth)) * 9;
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
