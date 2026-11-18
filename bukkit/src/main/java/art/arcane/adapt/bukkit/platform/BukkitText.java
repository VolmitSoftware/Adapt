package art.arcane.adapt.bukkit.platform;

import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Adapt markup on Bukkit, normalised the same way the plugin's language files are: legacy colour codes and
 * hex runs become MiniMessage tags, invalid MiniMessage falls back to legacy parsing. Item names, lore and
 * entity names are written as legacy section strings.
 */
public final class BukkitText {
  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
      .character('§')
      .hexColors()
      .useUnusualXRepeatedCharacterHexFormat()
      .build();

  private BukkitText() {
  }

  /** Markup as a legacy section string; null stays null, text without tags is returned as is. */
  public static String legacy(String markup) {
    if (markup == null || markup.indexOf('<') < 0) {
      return markup;
    }
    return ComponentText.markup(markup).legacy();
  }

  public static String legacy(Component component) {
    return component == null ? null : LegacyComponentSerializer.legacySection().serialize(component);
  }

  /** A legacy section string as a component, without reading MiniMessage tags. */
  public static Component legacyComponent(String legacy) {
    return legacy == null ? null : LegacyComponentSerializer.legacySection().deserialize(legacy);
  }

  public static Component component(String markup) {
    if (markup == null || markup.isEmpty()) {
      return Component.empty();
    }
    try {
      return MINI_MESSAGE.deserialize(ComponentText.normalizeMarkup(markup));
    } catch (RuntimeException invalid) {
      return LEGACY.deserialize(ComponentText.markup(markup).legacy());
    }
  }

  /** Item names and lore: italics off unless the markup turns them on. */
  public static Component itemText(String markup) {
    return Component.empty().decoration(TextDecoration.ITALIC, false).append(component(markup));
  }

  public static String markup(Component component) {
    return component == null ? "" : MINI_MESSAGE.serialize(component);
  }
}
