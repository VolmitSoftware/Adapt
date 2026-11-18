package art.arcane.adapt.mod.platform.text;

/**
 * Adapt markup normalisation, matching the Bukkit build: {@code &} and section colour codes,
 * {@code &#RRGGBB}, {@code &x&R&R&G&G&B&B}, {@code [RRGGBB]} and section hex runs become
 * MiniMessage tags; existing MiniMessage tags pass through untouched.
 */
public final class Markup {
    private static final char SECTION = '§';
    private static final String CODES = "0123456789abcdefklmnorx";

    private Markup() {
    }

    public static String toMiniMessage(String markup) {
        if (markup == null || markup.isEmpty()) {
            return "";
        }
        return sectionToMiniMessage(bracketHex(ampersand(markup)));
    }

    static String ampersand(String input) {
        if (input.indexOf('&') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        int length = input.length();
        for (int index = 0; index < length; index++) {
            char current = input.charAt(index);
            if (current == '\\' && index + 1 < length && input.charAt(index + 1) == '&') {
                out.append('&');
                index++;
                continue;
            }
            if (current != '&' || index + 1 >= length) {
                out.append(current);
                continue;
            }
            char next = Character.toLowerCase(input.charAt(index + 1));
            if (next == '#' && index + 7 < length && isHex(input, index + 2, 6)) {
                appendHex(out, input.substring(index + 2, index + 8));
                index += 7;
                continue;
            }
            if (next == 'x') {
                String expanded = expandedHex(input, index, '&');
                if (expanded != null) {
                    appendHex(out, expanded);
                    index += 13;
                    continue;
                }
                if (index + 7 < length && isHex(input, index + 2, 6)) {
                    appendHex(out, input.substring(index + 2, index + 8));
                    index += 7;
                    continue;
                }
            }
            if (CODES.indexOf(next) >= 0 && next != 'x') {
                out.append(SECTION).append(next);
                index++;
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }

    static String bracketHex(String input) {
        if (input.length() < 8 || input.indexOf('[') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        int length = input.length();
        for (int index = 0; index < length; index++) {
            char current = input.charAt(index);
            if (current == '\\' && index + 1 < length && input.charAt(index + 1) == '[') {
                out.append('[');
                index++;
                continue;
            }
            if (current == '[' && index + 7 < length && input.charAt(index + 7) == ']' && isHex(input, index + 1, 6)) {
                appendHex(out, input.substring(index + 1, index + 7));
                index += 7;
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }

    static String sectionToMiniMessage(String source) {
        if (source.indexOf(SECTION) < 0) {
            return source;
        }
        StringBuilder out = new StringBuilder(source.length() + 32);
        int length = source.length();
        for (int index = 0; index < length; index++) {
            char current = source.charAt(index);
            if (current != SECTION || index + 1 >= length) {
                out.append(current);
                continue;
            }
            char code = Character.toLowerCase(source.charAt(index + 1));
            if (code == 'x') {
                String hex = expandedHex(source, index, SECTION);
                if (hex != null) {
                    out.append("<reset><#").append(hex).append('>');
                    index += 13;
                    continue;
                }
            }
            String tag = tag(code);
            if (tag == null) {
                out.append(current).append(source.charAt(index + 1));
                index++;
                continue;
            }
            if ("0123456789abcdef".indexOf(code) >= 0) {
                out.append("<reset>");
            }
            out.append('<').append(tag).append('>');
            index++;
        }
        return out.toString();
    }

    private static void appendHex(StringBuilder out, String hex) {
        out.append(SECTION).append('x');
        for (int index = 0; index < 6; index++) {
            out.append(SECTION).append(Character.toLowerCase(hex.charAt(index)));
        }
    }

    private static String expandedHex(String source, int offset, char marker) {
        if (offset + 13 >= source.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            int markerIndex = offset + 2 + (index * 2);
            if (source.charAt(markerIndex) != marker) {
                return null;
            }
            char value = source.charAt(markerIndex + 1);
            if (Character.digit(value, 16) < 0) {
                return null;
            }
            hex.append(Character.toLowerCase(value));
        }
        return hex.toString();
    }

    private static boolean isHex(String source, int start, int count) {
        if (start + count > source.length()) {
            return false;
        }
        for (int index = start; index < start + count; index++) {
            if (Character.digit(source.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String tag(char code) {
        return switch (code) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }
}
