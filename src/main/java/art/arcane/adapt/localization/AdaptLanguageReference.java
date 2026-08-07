package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralValue;
import art.arcane.volmlib.util.localization.TextValue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class AdaptLanguageReference {
  private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

  private AdaptLanguageReference() {
  }

  public static File folder() {
    return new File(Adapt.instance.getDataFolder(), "languages");
  }

  public static File referenceFile() {
    return new File(folder(), AdaptMessages.catalog().englishLocale() + ".toml");
  }

  public static boolean write() {
    return LanguageFileWriter.write(
        referenceFile().toPath(),
        "language reference",
        render(AdaptMessages.catalog())
    );
  }

  static String render(MessageCatalog catalog) {
    Section root = new Section();
    for (MessageKey key : catalog.keys()) {
      root.put(key.id(), key.englishValue());
    }

    StringBuilder out = new StringBuilder();
    for (String line : header(catalog.englishLocale())) {
      out.append("# ").append(line).append('\n');
    }
    root.render(out, "");
    return out.toString();
  }

  private static List<String> header(String locale) {
    return List.of(
        "Adapt language reference: " + locale,
        "Generated from the code-owned English catalog and regenerated on every boot.",
        "Edits to this file are ignored.",
        "Copy the keys you want to change into languages/overrides/" + locale + ".toml."
    );
  }

  private static String formatKey(String key) {
    return BARE_KEY.matcher(key).matches() ? key : '"' + escape(key) + '"';
  }

  private static String formatPath(String path) {
    String[] segments = path.split("\\.", -1);
    StringBuilder out = new StringBuilder(path.length() + 4);
    for (int index = 0; index < segments.length; index++) {
      if (index > 0) {
        out.append('.');
      }
      out.append(formatKey(segments[index]));
    }
    return out.toString();
  }

  private static String formatText(String value) {
    return '"' + escape(value) + '"';
  }

  private static String formatLines(List<String> lines) {
    StringBuilder out = new StringBuilder();
    out.append('[');
    for (int index = 0; index < lines.size(); index++) {
      if (index > 0) {
        out.append(", ");
      }
      out.append(formatText(lines.get(index)));
    }
    return out.append(']').toString();
  }

  private static String escape(String value) {
    StringBuilder out = new StringBuilder(value.length() + 8);
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '\\' -> out.append("\\\\");
        case '"' -> out.append("\\\"");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (character < 0x20 || character == 0x7F) {
            out.append(String.format("\\u%04X", (int) character));
          } else {
            out.append(character);
          }
        }
      }
    }
    return out.toString();
  }

  private static String join(String path, String key) {
    return path.isEmpty() ? key : path + "." + key;
  }

  private static final class Section {
    private final Map<String, MessageValue> values = new LinkedHashMap<>();
    private final Map<String, Section> children = new LinkedHashMap<>();

    private void put(String id, MessageValue value) {
      Section section = this;
      int cursor = 0;
      while (true) {
        int dot = id.indexOf('.', cursor);
        if (dot < 0) {
          break;
        }
        String segment = id.substring(cursor, dot);
        if (section.values.containsKey(segment)) {
          throw new IllegalStateException("Message id collides with a message key: " + id);
        }
        section = section.children.computeIfAbsent(segment, ignored -> new Section());
        cursor = dot + 1;
      }

      String leaf = id.substring(cursor);
      if (section.children.containsKey(leaf)) {
        throw new IllegalStateException("Message id collides with a message group: " + id);
      }
      section.values.put(leaf, value);
    }

    private boolean hasInlineValues() {
      for (MessageValue value : values.values()) {
        if (!(value instanceof PluralValue)) {
          return true;
        }
      }
      return false;
    }

    private void render(StringBuilder out, String path) {
      if (hasInlineValues()) {
        if (path.isEmpty()) {
          if (!out.isEmpty()) {
            out.append('\n');
          }
        } else {
          openTable(out, path);
        }
        renderInlineValues(out);
      }

      for (Map.Entry<String, MessageValue> entry : values.entrySet()) {
        if (entry.getValue() instanceof PluralValue plural) {
          openTable(out, join(path, entry.getKey()));
          for (Map.Entry<String, String> form : plural.forms().entrySet()) {
            out.append(formatKey(form.getKey())).append(" = ").append(formatText(form.getValue())).append('\n');
          }
        }
      }

      for (Map.Entry<String, Section> child : children.entrySet()) {
        child.getValue().render(out, join(path, child.getKey()));
      }
    }

    private void renderInlineValues(StringBuilder out) {
      for (Map.Entry<String, MessageValue> entry : values.entrySet()) {
        MessageValue value = entry.getValue();
        if (value instanceof TextValue text) {
          out.append(formatKey(entry.getKey())).append(" = ").append(formatText(text.template())).append('\n');
        } else if (value instanceof LinesValue lines) {
          out.append(formatKey(entry.getKey())).append(" = ").append(formatLines(lines.lines())).append('\n');
        }
      }
    }

    private void openTable(StringBuilder out, String path) {
      if (!out.isEmpty()) {
        out.append('\n');
      }
      out.append('[').append(formatPath(path)).append(']').append('\n');
    }
  }
}
