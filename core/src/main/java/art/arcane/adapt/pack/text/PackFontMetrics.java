package art.arcane.adapt.pack.text;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Widths of text in the vanilla default font, in GUI pixels, as the client measures them: each glyph's advance (the
 * one pixel gap included), bold adding one pixel to bitmap glyphs and half a pixel to unifont ones, and the sum rounded
 * up. Legacy section-sign codes take no room; {@code l} turns bold on, {@code r} and colour codes turn it off. The table
 * ({@code vanilla-font.bin}) is measured from the client's own font files by {@code VanillaFontTable} in the pack
 * project; codepoints it lacks get the client's missing-glyph advance.
 */
public final class PackFontMetrics {
    public static final int LINE_HEIGHT = 9;
    private static final String RESOURCE = "vanilla-font.bin";
    private static final int MAGIC = 0x41465731;
    private static final int UNIHEX = 0x40;
    private static final int MISSING_ADVANCE = 6;
    private static final char SECTION = '§';
    private static final String COLOURS = "0123456789abcdef";

    private final String version;
    private final int[] starts;
    private final byte[][] runs;
    /** Whether only the code points the table lists can be set in this font; the vanilla font draws every one. */
    private final boolean listedOnly;

    private PackFontMetrics(String version, int[] starts, byte[][] runs, boolean listedOnly) {
        this.version = version;
        this.starts = starts;
        this.runs = runs;
        this.listedOnly = listedOnly;
    }

    /**
     * The metrics of a pack font that draws only the code points of {@code advances} (code point to advance in GUI
     * pixels, the one pixel gap included), such as the pack's condensed font.
     */
    public static PackFontMetrics of(String version, Map<Integer, Integer> advances) {
        TreeMap<Integer, Integer> sorted = new TreeMap<>(advances);
        List<Integer> starts = new ArrayList<>();
        List<byte[]> runs = new ArrayList<>();
        List<Byte> run = new ArrayList<>();
        int expected = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> entry : sorted.entrySet()) {
            if (entry.getKey() != expected && !run.isEmpty()) {
                runs.add(bytes(run));
                run.clear();
            }
            if (run.isEmpty()) {
                starts.add(entry.getKey());
            }
            run.add((byte) (int) entry.getValue());
            expected = entry.getKey() + 1;
        }
        if (!run.isEmpty()) {
            runs.add(bytes(run));
        }
        int[] startArray = new int[starts.size()];
        for (int index = 0; index < startArray.length; index++) {
            startArray[index] = starts.get(index);
        }
        return new PackFontMetrics(version, startArray, runs.toArray(new byte[0][]), true);
    }

    private static byte[] bytes(List<Byte> values) {
        byte[] out = new byte[values.size()];
        for (int index = 0; index < out.length; index++) {
            out[index] = values.get(index);
        }
        return out;
    }

    /** Whether this font can draw every code point of {@code text} (section-sign codes skipped). */
    public boolean covers(String text) {
        if (!listedOnly || text == null) {
            return true;
        }
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == SECTION) {
                index += 2;
                continue;
            }
            int point = text.codePointAt(index);
            int found = Arrays.binarySearch(starts, point);
            int at = found >= 0 ? found : -found - 2;
            if (at < 0 || point - starts[at] >= runs[at].length) {
                return false;
            }
            index += Character.charCount(point);
        }
        return true;
    }

    private static final class Holder {
        private static final PackFontMetrics VANILLA = load();
    }

    /** The vanilla default font's metrics, read once. */
    public static PackFontMetrics vanilla() {
        return Holder.VANILLA;
    }

    private static PackFontMetrics load() {
        try (InputStream raw = PackFontMetrics.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            return read(raw);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static PackFontMetrics read(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("not a font width table");
            }
            String version = in.readUTF();
            List<Integer> starts = new ArrayList<>();
            List<byte[]> runs = new ArrayList<>();
            for (int start = in.readInt(); start >= 0; start = in.readInt()) {
                byte[] run = new byte[in.readInt()];
                in.readFully(run);
                starts.add(start);
                runs.add(run);
            }
            int[] startArray = new int[starts.size()];
            for (int index = 0; index < startArray.length; index++) {
                startArray[index] = starts.get(index);
            }
            return new PackFontMetrics(version, startArray, runs.toArray(new byte[0][]), false);
        }
    }

    /** The client version the table was measured from. */
    public String version() {
        return version;
    }

    public int lineHeight() {
        return LINE_HEIGHT;
    }

    /** One glyph's advance, rounded up as the client rounds a whole string. */
    public int width(int codePoint, boolean bold) {
        return (halfWidth(codePoint, bold) + 1) / 2;
    }

    /**
     * Whether {@code text} fits {@code width}: its inked pixels do, since the last glyph's advance ends in a blank
     * column of spacing.
     */
    public boolean fits(String text, int width) {
        return width(text) - 1 <= width;
    }

    /** The width of {@code text}, section-sign codes skipped. */
    public int width(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int half = 0;
        boolean bold = false;
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == SECTION) {
                if (index + 1 < text.length()) {
                    bold = boldAfter(Character.toLowerCase(text.charAt(index + 1)), bold);
                }
                index += 2;
                continue;
            }
            int point = text.codePointAt(index);
            half += halfWidth(point, bold);
            index += Character.charCount(point);
        }
        return (half + 1) / 2;
    }

    /**
     * {@code text} broken into lines no wider than {@code maxWidth} the way the client wraps: at the last space before the
     * overflow, or before the overflowing glyph when the line has no space, and at every newline. The space or newline
     * at a break is dropped. Codes still in force at a break start the next line again, since each line is sent alone.
     */
    public List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        String rest = text;
        String carried = "";
        while (true) {
            int split = split(rest, maxWidth);
            if (split >= rest.length()) {
                lines.add(carried + rest);
                return lines;
            }
            String line = rest.substring(0, split);
            lines.add(carried + line);
            carried = activeCodes(carried + line);
            char at = rest.charAt(split);
            rest = rest.substring(split + (at == ' ' || at == '\n' ? 1 : 0));
        }
    }

    /**
     * {@code text} (plain, no codes) broken into lines of at most {@code maxWidth} at spaces only: a word wider than a line
     * keeps a line of its own and overflows it, so the caller cuts it short instead of breaking it inside.
     */
    public List<String> wrapWords(String text, int maxWidth) {
        return wrapWords(text, maxWidth, false);
    }

    /**
     * {@code text} (plain, no codes) broken into lines of at most {@code maxWidth} at spaces; with {@code breaks}, a word
     * wider than a line is split over lines, each part but the last ending in an added hyphen (see {@link Hyphenation}).
     */
    public List<String> wrapWords(String text, int maxWidth, boolean breaks) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!line.isEmpty() && !fits(line + " " + word, maxWidth)) {
                lines.add(line.toString());
                line.setLength(0);
            }
            String rest = word;
            while (breaks && line.isEmpty() && !fits(rest, maxWidth)) {
                int cut = Hyphenation.breakAt(this, rest, maxWidth);
                if (cut < 1) {
                    break;
                }
                lines.add(rest.substring(0, cut) + Hyphenation.HYPHEN);
                rest = rest.substring(cut);
            }
            line.append(line.isEmpty() ? "" : " ").append(rest);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Where the first line of {@code text} ends: a newline, the last space before the overflow, or the overflow. */
    private int split(String text, int maxWidth) {
        int half = 0;
        int limit = maxWidth * 2;
        int lastSpace = -1;
        boolean inked = false;
        boolean bold = false;
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == SECTION) {
                if (index + 1 < text.length()) {
                    bold = boldAfter(Character.toLowerCase(text.charAt(index + 1)), bold);
                }
                index += 2;
                continue;
            }
            if (c == '\n') {
                return index;
            }
            if (c == ' ') {
                lastSpace = index;
            }
            int point = text.codePointAt(index);
            half += halfWidth(point, bold);
            if (half > limit && inked) {
                return lastSpace != -1 ? lastSpace : index;
            }
            inked |= half != 0;
            index += Character.charCount(point);
        }
        return text.length();
    }

    private static String activeCodes(String text) {
        String colour = "";
        StringBuilder styles = new StringBuilder();
        for (int index = 0; index + 1 < text.length(); index++) {
            if (text.charAt(index) != SECTION) {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            if (COLOURS.indexOf(code) >= 0) {
                colour = "" + SECTION + code;
                styles.setLength(0);
            } else if (code == 'r') {
                colour = "";
                styles.setLength(0);
            } else if ("klmno".indexOf(code) >= 0 && styles.indexOf("" + SECTION + code) < 0) {
                styles.append(SECTION).append(code);
            }
            index++;
        }
        return colour + styles;
    }

    private static boolean boldAfter(char code, boolean bold) {
        if (code == 'l') {
            return true;
        }
        return (code == 'r' || COLOURS.indexOf(code) >= 0) ? false : bold;
    }

    private int halfWidth(int point, boolean bold) {
        int found = Arrays.binarySearch(starts, point);
        int run = found >= 0 ? found : -found - 2;
        if (run >= 0 && point - starts[run] < runs[run].length) {
            int value = runs[run][point - starts[run]];
            boolean unihex = (value & UNIHEX) != 0;
            return (value & ~UNIHEX) * 2 + (bold ? unihex ? 1 : 2 : 0);
        }
        return MISSING_ADVANCE * 2 + (bold ? 2 : 0);
    }
}
