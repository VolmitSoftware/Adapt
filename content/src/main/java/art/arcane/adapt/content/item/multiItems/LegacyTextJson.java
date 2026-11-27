package art.arcane.adapt.content.item.multiItems;


import art.arcane.adapt.content.data.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts legacy section-coded text into vanilla text-component JSON with the same colours and decorations. */
final class LegacyTextJson {
  private static final char SECTION = '§';
  private static final String[] COLORS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
      "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

  private LegacyTextJson() {
  }

  static String toJson(String legacy) {
    List<Map<String, Object>> parts = new ArrayList<>();
    Style style = new Style();
    StringBuilder text = new StringBuilder();
    String source = legacy == null ? "" : legacy;
    int i = 0;
    while (i < source.length()) {
      char c = source.charAt(i);
      if (c != SECTION || i + 1 >= source.length()) {
        text.append(c);
        i++;
        continue;
      }
      char code = Character.toLowerCase(source.charAt(i + 1));
      String hex = code == 'x' ? hex(source, i) : null;
      int colorIndex = Character.digit(code, 16);
      if (hex == null && colorIndex < 0 && "klmnor".indexOf(code) < 0) {
        text.append(c);
        i++;
        continue;
      }
      flush(parts, text, style);
      if (hex != null) {
        style = new Style();
        style.color = hex;
        i += 14;
        continue;
      }
      if (colorIndex >= 0) {
        style = new Style();
        style.color = COLORS[colorIndex];
      } else {
        switch (code) {
          case 'k' -> style.obfuscated = true;
          case 'l' -> style.bold = true;
          case 'm' -> style.strikethrough = true;
          case 'n' -> style.underlined = true;
          case 'o' -> style.italic = true;
          default -> style = new Style();
        }
      }
      i += 2;
    }
    flush(parts, text, style);

    if (parts.isEmpty()) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("text", "");
      return Json.writeComponent(empty);
    }
    if (parts.size() == 1) {
      return Json.writeComponent(parts.get(0));
    }
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("text", "");
    root.put("extra", parts);
    return Json.writeComponent(root);
  }

  private static String hex(String source, int start) {
    if (start + 14 > source.length()) {
      return null;
    }
    StringBuilder digits = new StringBuilder("#");
    for (int j = start + 2; j < start + 14; j += 2) {
      if (source.charAt(j) != SECTION || Character.digit(source.charAt(j + 1), 16) < 0) {
        return null;
      }
      digits.append(Character.toLowerCase(source.charAt(j + 1)));
    }
    return digits.toString();
  }

  private static void flush(List<Map<String, Object>> parts, StringBuilder text, Style style) {
    if (text.isEmpty()) {
      return;
    }
    Map<String, Object> part = new LinkedHashMap<>();
    part.put("text", text.toString());
    if (style.color != null) {
      part.put("color", style.color);
    }
    if (style.obfuscated) {
      part.put("obfuscated", true);
    }
    if (style.bold) {
      part.put("bold", true);
    }
    if (style.strikethrough) {
      part.put("strikethrough", true);
    }
    if (style.underlined) {
      part.put("underlined", true);
    }
    if (style.italic) {
      part.put("italic", true);
    }
    parts.add(part);
    text.setLength(0);
  }

  private static final class Style {
    private String color;
    private boolean obfuscated;
    private boolean bold;
    private boolean strikethrough;
    private boolean underlined;
    private boolean italic;
  }
}
