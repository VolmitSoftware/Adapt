package art.arcane.adapt.pack.text;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Builds the advance table {@code PackFontMetrics} reads from a vanilla client jar and the unifont asset, the way the
 * client measures them: bitmap glyphs by their rightmost opaque column, unihex glyphs by their inked span, the space
 * provider by its declared advances. Only the widths are kept, plus the bitmap providers' character grids so text fonts
 * can point at the client's own sheets at other ascents; no glyph art leaves the client files.
 * <p>Usage: {@code VanillaFontTable <client.jar> <unifont.zip> <version> <widths.bin> <providers.json>}.
 */
public final class VanillaFontTable {
    static final int UNIHEX = 0x40;

    private VanillaFontTable() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 5) {
            throw new IllegalArgumentException("usage: VanillaFontTable <client.jar> <unifont.zip> <version> <widths.bin> <providers.json>");
        }
        JsonArray grids = new JsonArray();
        Map<Integer, Integer> table = new TreeMap<>();
        try (ZipFile client = new ZipFile(args[0])) {
            space(read(client, "assets/minecraft/font/include/space.json"), table);
            JsonObject defaults = JsonParser.parseString(new String(read(client, "assets/minecraft/font/include/default.json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : defaults.getAsJsonArray("providers")) {
                JsonObject provider = element.getAsJsonObject();
                if (provider.get("type").getAsString().equals("bitmap")) {
                    bitmap(client, provider, table);
                    grids.add(provider);
                }
            }
        }
        unihex(Files.readAllBytes(Path.of(args[1])), table);
        Files.write(Path.of(args[3]), encode(args[2], table));
        JsonObject providers = new JsonObject();
        providers.addProperty("version", args[2]);
        providers.add("providers", grids);
        Files.writeString(Path.of(args[4]), new GsonBuilder().disableHtmlEscaping().create().toJson(providers) + "\n", StandardCharsets.UTF_8);
    }

    private static byte[] read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException("missing " + name);
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static void space(byte[] json, Map<Integer, Integer> table) {
        JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement element : root.getAsJsonArray("providers")) {
            JsonObject advances = element.getAsJsonObject().getAsJsonObject("advances");
            for (Map.Entry<String, JsonElement> entry : advances.entrySet()) {
                table.putIfAbsent(entry.getKey().codePointAt(0), entry.getValue().getAsInt());
            }
        }
    }

    private static void bitmap(ZipFile client, JsonObject provider, Map<Integer, Integer> table) throws IOException {
        String file = provider.get("file").getAsString();
        String path = "assets/minecraft/textures/" + file.substring(file.indexOf(':') + 1);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(read(client, path)));
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        int rows = provider.getAsJsonArray("chars").size();
        int columns = provider.getAsJsonArray("chars").get(0).getAsString().codePointCount(0,
                provider.getAsJsonArray("chars").get(0).getAsString().length());
        int cellWidth = image.getWidth() / columns;
        int cellHeight = image.getHeight() / rows;
        float scale = (float) height / cellHeight;
        for (int row = 0; row < rows; row++) {
            int[] points = provider.getAsJsonArray("chars").get(row).getAsString().codePoints().toArray();
            for (int column = 0; column < points.length; column++) {
                int point = points[column];
                if (point == 0) {
                    continue;
                }
                int width = inked(image, cellWidth, cellHeight, column, row);
                table.putIfAbsent(point, (int) (0.5 + width * scale) + 1);
            }
        }
    }

    private static int inked(BufferedImage image, int cellWidth, int cellHeight, int column, int row) {
        for (int x = cellWidth - 1; x >= 0; x--) {
            for (int y = 0; y < cellHeight; y++) {
                if ((image.getRGB(column * cellWidth + x, row * cellHeight + y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }

    private static void unihex(byte[] zip, Map<Integer, Integer> table) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (!entry.getName().endsWith(".hex")) {
                    continue;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    int colon = line.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    int point = Integer.parseInt(line.substring(0, colon), 16);
                    if (table.containsKey(point)) {
                        continue;
                    }
                    table.put(point, UNIHEX | unihexAdvance(point, line.substring(colon + 1)));
                }
            }
        }
    }

    /** The client's unihex advance: half the inked span plus one, with the CJK and Hangul spans fixed by its overrides. */
    static int unihexAdvance(int point, String hex) {
        int digits = hex.length() / 16;
        int bits = digits * 4;
        int left;
        int right;
        if (fixedFullWidth(point)) {
            left = point >= 0xAC00 && point <= 0xD7AF ? 1 : 0;
            right = 15;
        } else {
            int mask = 0;
            for (int row = 0; row < 16; row++) {
                int line = (int) Long.parseLong(hex.substring(row * digits, row * digits + digits), 16);
                mask |= line << (32 - bits);
            }
            if (mask == 0) {
                left = 0;
                right = bits;
            } else {
                left = Integer.numberOfLeadingZeros(mask);
                right = 32 - Integer.numberOfTrailingZeros(mask) - 1;
            }
        }
        return (right - left + 1) / 2 + 1;
    }

    private static boolean fixedFullWidth(int point) {
        return point >= 0x3001 && point <= 0x30FF || point >= 0x3200 && point <= 0x9FFF || point >= 0x1100 && point <= 0x11FF
                || point >= 0x3130 && point <= 0x318F || point >= 0xA960 && point <= 0xA97F || point >= 0xD7B0 && point <= 0xD7FF
                || point >= 0xAC00 && point <= 0xD7AF || point >= 0xF900 && point <= 0xFAFF || point >= 0xFF01 && point <= 0xFF5E;
    }

    /** Runs of consecutive codepoints: start, count, then one byte each (advance, plus {@link #UNIHEX} for half-pixel bold). */
    static byte[] encode(String version, Map<Integer, Integer> table) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream gzip = new GZIPOutputStream(bytes); DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeInt(0x41465731);
            out.writeUTF(version);
            Integer[] points = table.keySet().toArray(new Integer[0]);
            int index = 0;
            while (index < points.length) {
                int end = index;
                while (end + 1 < points.length && points[end + 1] == points[end] + 1) {
                    end++;
                }
                out.writeInt(points[index]);
                out.writeInt(end - index + 1);
                for (int at = index; at <= end; at++) {
                    out.writeByte(table.get(points[at]));
                }
                index = end + 1;
            }
            out.writeInt(-1);
        }
        return bytes.toByteArray();
    }
}
