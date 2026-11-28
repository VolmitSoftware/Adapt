package art.arcane.adapt.pack.manifest;

import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.pack.text.RowFont;
import art.arcane.adapt.pack.text.ShiftFont;
import art.arcane.adapt.platform.value.Id;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the baked pack holds, as the server needs it to compose windows: the title line, the space font, the row fonts
 * text can be placed in, the page art tiles per kit and page kind, item models for icons, chrome and sprites, tooltip
 * frames per kit, paint glyphs (slot faces, header chips, bars and rules) by their {@code PackArt} key, the action bar
 * glyph per sprite and the toast icon model per kit. Read by {@link PackManifestReader}.
 */
public record PackManifest(int format, RowFont title, int titleX, ShiftFont shift, List<RowFont> rows, Map<String, PageArt> pages,
                           Map<String, List<PackSlot>> hostSlots, Map<String, ItemArt> items, Map<String, TooltipArt> tooltips,
                           Map<String, PaintArt> paint, Map<String, String> hudGlyphs, Map<String, Id> toastModels, Condensed condensed) {
  public static final String FALLBACK_KIT = "adapt:tome";
  private static final String SPRITE_PREFIX = "sprite:";
  /** The icon id suffix of an icon's greyed copy. */
  public static final String DIM = "#dim";

  /** The item type of every pack-drawn slot; its item model decides the picture. */
  public static final Id CARRIER = Id.minecraft("book");

  public PackManifest {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(shift, "shift");
    rows = rows.stream().sorted(Comparator.comparingInt(RowFont::y)).toList();
    pages = Map.copyOf(pages);
    hostSlots = Map.copyOf(hostSlots);
    items = Map.copyOf(items);
    tooltips = Map.copyOf(tooltips);
    paint = Map.copyOf(paint);
    hudGlyphs = Map.copyOf(hudGlyphs);
    toastModels = Map.copyOf(toastModels);
  }

  /** The page art of layout {@code kind} in {@code kit} ({@link PackKeys#kit}), the neutral kit's when the kit has none, or null. */
  public PageArt page(String kit, String kind) {
    PageArt art = pages.get(kit + "/" + kind);
    return art != null ? art : pages.get(FALLBACK_KIT + "/" + kind);
  }

  /** The item named {@code key} ({@code blank}, {@code ctl/back} ...), or null. */
  public ItemArt item(String key) {
    return items.get(key);
  }

  /**
   * The item that draws the sprite {@code spriteId} in any form a view model carries it ({@code ns:gui/skill/x},
   * {@code ns:textures/gui/skill/x.png}, stacked layers by their bottom one), its greyed copy when {@code dim} and the
   * pack has one; null when the pack does not list the sprite.
   */
  public ItemArt sprite(String spriteId, boolean dim) {
    String packId = PackSprites.packId(spriteId);
    if (packId == null) {
      return null;
    }
    ItemArt greyed = dim ? items.get(SPRITE_PREFIX + packId + DIM) : null;
    return greyed != null ? greyed : items.get(SPRITE_PREFIX + packId);
  }

  /** The tooltip frame of {@code kit}, the neutral kit's, or the vanilla tooltip. */
  public TooltipArt tooltip(String kit) {
    TooltipArt art = tooltips.get(kit);
    if (art == null) {
      art = tooltips.get(FALLBACK_KIT);
    }
    return art == null ? TooltipArt.VANILLA : art;
  }

  /** The row font whose line top is nearest {@code y}; the title line when {@code y} is on it. */
  public RowFont row(int y) {
    RowFont best = title;
    for (RowFont row : rows) {
      if (Math.abs(row.y() - y) < Math.abs(best.y() - y)) {
        best = row;
      }
    }
    return best;
  }

  /**
   * Page art: tiles of at most 256 pixels, each a glyph of {@code font} whose left edge sits at container {@code x},
   * and the host items that carry the page's animated dressing, each in its own slot.
   */
  public record PageArt(List<Tile> tiles, List<Host> hosts) {
    public PageArt {
      tiles = List.copyOf(tiles);
      hosts = List.copyOf(hosts);
    }
  }

  /** An item of animated dressing and the slot it is drawn from; the slot holds nothing else. */
  public record Host(PackSlot slot, ItemArt art) {
    public Host {
      Objects.requireNonNull(slot, "slot");
      Objects.requireNonNull(art, "art");
    }
  }

  public record Tile(String font, String text, int x, int advance) {
    public Tile {
      Objects.requireNonNull(font, "font");
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * The pack's condensed font for names too long for the default one: its widths and its row fonts by line top, as
   * the default text has them. Null on a pack without it.
   */
  public record Condensed(PackFontMetrics metrics, RowFont title, List<RowFont> rows) {
    public Condensed {
      Objects.requireNonNull(metrics, "metrics");
      Objects.requireNonNull(title, "title");
      rows = rows.stream().sorted(Comparator.comparingInt(RowFont::y)).toList();
    }

    /** The row font whose line top is nearest {@code y}. */
    public RowFont row(int y) {
      RowFont best = title;
      for (RowFont row : rows) {
        if (Math.abs(row.y() - y) < Math.abs(best.y() - y)) {
          best = row;
        }
      }
      return best;
    }
  }

  /** An item model id. */
  public record ItemArt(Id model) {
    public ItemArt {
      Objects.requireNonNull(model, "model");
    }
  }

  /** A tooltip frame ({@code style} null for vanilla) and the ARGB shadow its text lines take. */
  public record TooltipArt(String style, int shadow) {
    public static final TooltipArt VANILLA = new TooltipArt(null, -1);
  }

  /**
   * A paint glyph: {@code text} of {@code font}, whose left edge goes {@code dx} pixels right of the x it is drawn at
   * and which moves the pen {@code advance} pixels.
   */
  public record PaintArt(String font, String text, int dx, int advance) {
    public PaintArt {
      Objects.requireNonNull(font, "font");
      Objects.requireNonNull(text, "text");
    }
  }
}
