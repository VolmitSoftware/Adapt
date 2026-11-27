package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.pack.surface.TitleRun;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.List;

/** Window titles from font runs. */
final class PackTitles {
  private PackTitles() {
  }

  static Component component(List<TitleRun> runs) {
    TextComponent.Builder title = Component.text();
    for (TitleRun run : runs) {
      title.append(Component.text(run.text(), TextColor.color(run.colour())).font(Key.key(run.font())));
    }
    return title.build();
  }

  static String json(List<TitleRun> runs) {
    return GsonComponentSerializer.gson().serialize(component(runs));
  }

  /**
   * The runs as plain text, for chest windows that cannot carry fonts. Pack glyphs (page art, bands, pen moves) are
   * private use characters that only the pack's fonts draw, so they are left out.
   */
  static String plain(List<TitleRun> runs) {
    StringBuilder text = new StringBuilder();
    for (TitleRun run : runs) {
      run.text().codePoints()
          .filter((int codePoint) -> Character.getType(codePoint) != Character.PRIVATE_USE)
          .forEach(text::appendCodePoint);
    }
    return text.toString().strip();
  }
}
