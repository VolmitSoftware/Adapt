package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The conventional name and lore of a custom item: the messages {@code <prefix>.name} and {@code .lore1},
 * {@code .lore2} and on. The prefix is {@link TextKeys#item}, except that a built-in mutation tool
 * {@code adapt:<skill>/tool/<name>}, or {@code adapt:mutated/<skill>/.../tool/<name>} below a pair led by that skill,
 * keeps the built-in bundle's {@code tool.<skill>.<name>}.
 */
public final class ItemTexts {
  private static final String TOOL = "/tool/";
  private static final String MUTATED = "mutated/";

  private ItemTexts() {
  }

  public static String prefix(Id item) {
    String path = item.path();
    int tool = path.lastIndexOf(TOOL);
    if (!Id.ADAPT.equals(item.namespace()) || tool <= 0 || path.indexOf('/', tool + TOOL.length()) >= 0) {
      return TextKeys.item(item);
    }
    String owner = path.substring(0, tool);
    if (owner.startsWith(MUTATED)) {
      int end = owner.indexOf('/', MUTATED.length());
      owner = end < 0 ? owner.substring(MUTATED.length()) : owner.substring(MUTATED.length(), end);
    }
    if (owner.indexOf('/') >= 0) {
      return TextKeys.item(item);
    }
    return Id.ADAPT + ".tool." + owner + "." + path.substring(tool + TOOL.length());
  }

  /** The item's name in the language of the current render, or null when no bundle declares it. */
  public static @Nullable String name(Id item) {
    String key = prefix(item) + ".name";
    return AdaptLanguage.declared(key) == null ? null : BridgeServices.resolve(Text.key(key));
  }

  /** The item's declared lore lines, first first, in the language of the current render. */
  public static List<String> lore(Id item) {
    String prefix = prefix(item);
    List<String> lines = new ArrayList<>();
    for (int line = 1; AdaptLanguage.declared(prefix + ".lore" + line) != null; line++) {
      lines.add(BridgeServices.resolve(Text.key(prefix + ".lore" + line)));
    }
    return lines;
  }
}
