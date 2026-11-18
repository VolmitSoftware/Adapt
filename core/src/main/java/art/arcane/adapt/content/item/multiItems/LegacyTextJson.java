package art.arcane.adapt.content.item.multiItems;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Converts legacy section-coded text into vanilla text-component JSON with the same colours and decorations. */
final class LegacyTextJson {
  private static final char SECTION = '§';
  private static final String[] COLORS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
      "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

  private LegacyTextJson() {
  }

  static String toJson(String legacy) {
    List<JsonObject> parts = new ArrayList<>();
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
      JsonObject empty = new JsonObject();
      empty.addProperty("text", "");
      return empty.toString();
    }
    if (parts.size() == 1) {
      return parts.get(0).toString();
    }
    JsonObject root = new JsonObject();
    root.addProperty("text", "");
    JsonArray extra = new JsonArray();
    for (JsonObject part : parts) {
      extra.add(part);
    }
    root.add("extra", extra);
    return root.toString();
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

  private static void flush(List<JsonObject> parts, StringBuilder text, Style style) {
    if (text.isEmpty()) {
      return;
    }
    JsonObject part = new JsonObject();
    part.addProperty("text", text.toString());
    if (style.color != null) {
      part.addProperty("color", style.color);
    }
    if (style.obfuscated) {
      part.addProperty("obfuscated", true);
    }
    if (style.bold) {
      part.addProperty("bold", true);
    }
    if (style.strikethrough) {
      part.addProperty("strikethrough", true);
    }
    if (style.underlined) {
      part.addProperty("underlined", true);
    }
    if (style.italic) {
      part.addProperty("italic", true);
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
