package art.arcane.adapt.pack;

import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.manifest.PackManifestReader;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.SlotCycle;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuIcon;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The sample manifest, the provisional layout and a readable dump of a rendered page for goldens. */
public final class PackSamples {
  private PackSamples() {
  }

  public static PackManifest manifest() {
    try (InputStream raw = PackSamples.class.getResourceAsStream("bake-manifest.json")) {
      return PackManifestReader.read(new InputStreamReader(raw, StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  public static PackResources resources() {
    return new PackResources(manifest(), PackLayoutReader.builtIn(), PackFontMetrics.vanilla());
  }

  /** Title runs, then every chest and player slot with its icon, click handler presence and look, one per line. */
  public static String dump(ChestMenu menu, PackSurface surface) {
    StringBuilder out = new StringBuilder();
    out.append("rows ").append(menu.rows()).append(surface.topRowsOnly() ? " top-only" : "").append('\n');
    for (TitleRun run : surface.title()) {
      out.append("run ").append(run.font()).append(' ').append(String.format("%06x", run.colour())).append(' ')
          .append(escape(run.text())).append('\n');
    }
    for (Map.Entry<Integer, MenuButton> entry : menu.buttons().entrySet()) {
      slot(out, PackSlot.top(entry.getKey()), entry.getValue(), surface.look(PackSlot.top(entry.getKey())));
    }
    for (Map.Entry<Integer, MenuButton> entry : surface.lower().entrySet()) {
      slot(out, PackSlot.lower(entry.getKey()), entry.getValue(), surface.look(PackSlot.lower(entry.getKey())));
    }
    for (SlotCycle cycle : surface.cycles()) {
      out.append("cycle ").append(cycle.slot()).append(" every ").append(cycle.periodTicks());
      for (MenuIcon frame : cycle.frames()) {
        out.append(' ').append(frame.type()).append('x').append(frame.amount());
      }
      out.append('\n');
    }
    return out.toString();
  }

  private static void slot(StringBuilder out, PackSlot slot, MenuButton button, SlotLook look) {
    MenuIcon icon = button.icon();
    out.append(slot).append(' ').append(icon.type()).append('x').append(icon.amount())
        .append(button.clickable() ? " click" : "").append(" \"").append(escape(icon.name())).append('"');
    for (String line : icon.lore()) {
      out.append(" | ").append(escape(line));
    }
    if (look != null) {
      out.append(" @").append(look.itemModel()).append(look.hideTooltip() ? " hidden" : "")
          .append(look.tooltipStyle() == null ? "" : " " + look.tooltipStyle())
          .append(look.sprite() == null ? "" : " sprite=" + look.sprite())
          .append(look.cooldownGroup() == null ? "" : " cooldown=" + look.cooldownGroup());
    }
    out.append('\n');
  }

  static String escape(String text) {
    if (text == null) {
      return "null";
    }
    StringBuilder out = new StringBuilder();
    for (int index = 0; index < text.length(); index++) {
      char c = text.charAt(index);
      if (c >= 0xE000 && c <= 0xF8FF || c == '§') {
        out.append(String.format("<%04x>", (int) c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
