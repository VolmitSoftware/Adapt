package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiControl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How an adaptation's name sits in the name block of its card, on at most a given number of lines, always at the font's
 * own size. Lines break at spaces and after a word's own hyphens; a word too wide for a line breaks inside with an added
 * hyphen where a syllable seems to end, or between any two letters where none does, leaving at least {@link #SHORT}
 * letters on each side, or between any two characters of a script without syllables, such as Japanese. Of the ways that fit, the
 * fewest lines win, then the fewest lines under {@link #SHORT} characters, a piece of a broken word weighing twice a whole
 * word, then the most even line lengths. A line may reach the pixel the font leaves blank after its last letter.
 */
final class CardName {
    static final int PITCH = 9;
    /** Lines shorter than this many characters are avoided when another break exists; a broken word keeps this many a side. */
    static final int SHORT = 4;
    /** What a short line weighs against uneven lines: more than any spread of widths a card can have. */
    private static final long SHORT_PENALTY = 1_000_000L;

    private CardName() {
    }

    /** The height of a name block of {@code lines} lines. */
    static int blockHeight(int lines) {
        return (lines - 1) * PITCH + UiControl.CAP_HEIGHT;
    }

    /** {@code name}'s lines in a block {@code width} wide on at most {@code lines} lines, or null when it cannot fit them. */
    static List<String> fit(UiDraw metrics, String name, int width, int lines) {
        int room = width + 1;
        List<String> whole = balanced(metrics, name, room, lines, false);
        return whole != null ? whole : balanced(metrics, name, room, lines, true);
    }

    /** How a piece of a name joins the piece before it, and what a line break there leaves at the end of the line. */
    private enum Join {
        SPACE,
        HYPHEN,
        SYLLABLE,
        CHARACTER
    }

    /** One piece of a name between two places it may break. */
    private record Atom(String text, Join join) {
    }

    /**
     * The best lines for {@code name} {@code width} wide, or null when none fit {@code lines}. A name breaks at spaces;
     * a word too wide for a line also after its own hyphens and, with {@code breakWords}, inside.
     */
    private static List<String> balanced(UiDraw metrics, String name, int width, int lines, boolean breakWords) {
        List<Atom> atoms = new ArrayList<>();
        for (String word : name.trim().split("\\s+")) {
            Join join = atoms.isEmpty() ? Join.HYPHEN : Join.SPACE;
            if (metrics.textWidth(word) <= width) {
                atoms.add(new Atom(word, join));
                continue;
            }
            String[] parts = word.split("(?<=-)(?=.)");
            for (int index = 0; index < parts.length; index++) {
                String part = parts[index];
                Join first = index == 0 ? join : Join.HYPHEN;
                if (metrics.textWidth(part) <= width || !breakWords) {
                    atoms.add(new Atom(part, first));
                    continue;
                }
                boolean letters = !NameLines.ideographic(part);
                String stem = part.endsWith("-") ? part.substring(0, part.length() - 1) : part;
                List<Integer> points = letters ? NameLines.syllables(stem, SHORT) : new ArrayList<>();
                if (points.isEmpty()) {
                    int edge = letters ? SHORT : 1;
                    for (int at = edge; at <= stem.length() - edge; at++) {
                        points.add(at);
                    }
                }
                int from = 0;
                for (int point : points) {
                    atoms.add(new Atom(part.substring(from, point), from == 0 ? first : letters ? Join.SYLLABLE : Join.CHARACTER));
                    from = point;
                }
                atoms.add(new Atom(part.substring(from), from == 0 ? first : letters ? Join.SYLLABLE : Join.CHARACTER));
            }
        }
        int count = atoms.size();
        long[][] cost = new long[lines + 1][count + 1];
        int[][] from = new int[lines + 1][count + 1];
        for (long[] row : cost) {
            Arrays.fill(row, Long.MAX_VALUE);
        }
        cost[0][0] = 0L;
        for (int line = 1; line <= lines; line++) {
            for (int end = 1; end <= count; end++) {
                for (int start = end - 1; start >= 0; start--) {
                    if (cost[line - 1][start] == Long.MAX_VALUE) {
                        continue;
                    }
                    String text = line(atoms, start, end);
                    int used = metrics.textWidth(text);
                    if (used > width) {
                        break;
                    }
                    long slack = width - used;
                    boolean fragment = start > 0 && atoms.get(start).join() != Join.SPACE;
                    long step = (text.length() < SHORT ? SHORT_PENALTY * (fragment ? 2L : 1L) : 0L) + slack * slack;
                    if (cost[line - 1][start] + step < cost[line][end]) {
                        cost[line][end] = cost[line - 1][start] + step;
                        from[line][end] = start;
                    }
                }
            }
            if (cost[line][count] != Long.MAX_VALUE) {
                List<String> found = new ArrayList<>();
                int end = count;
                for (int back = line; back > 0; back--) {
                    int start = from[back][end];
                    found.add(0, line(atoms, start, end));
                    end = start;
                }
                return found;
            }
        }
        return null;
    }

    /** The text of atoms {@code start} to {@code end} on one line, with the hyphen a syllable break leaves. */
    private static String line(List<Atom> atoms, int start, int end) {
        StringBuilder text = new StringBuilder(atoms.get(start).text());
        for (int index = start + 1; index < end; index++) {
            text.append(atoms.get(index).join() == Join.SPACE ? " " : "").append(atoms.get(index).text());
        }
        if (end < atoms.size() && atoms.get(end).join() == Join.SYLLABLE) {
            text.append('-');
        }
        return text.toString();
    }

    /** Draws {@code lines} from ({@code x}, {@code y}) in {@code color}. */
    static void paint(UiDraw draw, List<String> lines, int x, int y, int color) {
        for (int index = 0; index < lines.size(); index++) {
            draw.text(lines.get(index), x, y + index * PITCH, color, false);
        }
    }
}
