package art.arcane.adapt.pack;

import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.pack.layout.BarSpec;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.PackLayout;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.manifest.PackKeys;
import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.manifest.PackSprites;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.pack.text.PackInk;
import art.arcane.adapt.pack.text.Placement;
import art.arcane.adapt.pack.text.RowFont;
import art.arcane.adapt.pack.text.TitleComposer;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * The loaded pack as the renderer uses it: the bake's manifest, the screen geometry and the font metrics, with the
 * lookups every screen needs (page art, paint, label placement, item looks). Art keys are those of {@link PackArt}.
 */
public record PackResources(PackManifest manifest, PackLayout layout, PackFontMetrics metrics) {
  public static final String CRUMB_SEPARATOR = " / ";
  /** Labels of at most this many lines sit centred in them when shorter; longer blocks start at the top. */
  private static final int CENTRED_LINES = 2;
  private static final int CRUMB_PAPER = 0xF6EDD8;
  private static final int CRUMB_GILT = 0xF2D98A;
  private static final int CRUMB_RULE = 0xB79C6E;

  public PackResources {
    Objects.requireNonNull(manifest, "manifest");
    Objects.requireNonNull(layout, "layout");
    Objects.requireNonNull(metrics, "metrics");
  }

  public TitleComposer composer() {
    return new TitleComposer(metrics, manifest.shift(), manifest.titleX());
  }

  /**
   * The kit that dresses leaf {@code leaf} of a page for {@code skill}, a skill id or line key as views carry it, or a
   * kit id; a mutated skill's partner kit is drawn as the facing page.
   */
  public static PageKit kit(String skill, int leaf) {
    Id owner = PackKeys.kitSkill(skill, leaf);
    String kit = owner == null ? PackArt.TOME : PackKeys.kit(owner);
    return new PageKit(kit, PairKeys.parse(skill) != null && leaf % 2 == 1);
  }

  /** A page's kit, and whether it is drawn as the partner's facing page. */
  public record PageKit(String kit, boolean partner) {
    public PageKit {
      Objects.requireNonNull(kit, "kit");
    }
  }

  /**
   * Draws the page art of the screen's kinds in {@code kit}, skipping kinds the pack lacks, and returns the host items
   * of their animated dressing. A partner page draws {@code single} as {@code single-partner}; a screen without dressing
   * host slots draws the kind's still copy (see {@link PackArt#still}) when the pack has one.
   */
  public List<PackManifest.Host> page(TitleComposer title, ScreenLayout screen, PageKit kit) {
    List<PackManifest.Host> hosts = new ArrayList<>();
    for (String kind : screen.pageKind().split("\\+")) {
      PackManifest.PageArt art = pageArt(kit, kind, screen);
      if (art == null) {
        continue;
      }
      for (PackManifest.Tile tile : art.tiles()) {
        title.glyphAt(tile.font(), tile.text(), tile.x(), tile.advance());
      }
      hosts.addAll(art.hosts());
    }
    return hosts;
  }

  private PackManifest.PageArt pageArt(PageKit kit, String kind, ScreenLayout screen) {
    String layout = kit.partner() && kind.equals(PackArt.SINGLE) ? PackArt.PARTNER : kind;
    PackManifest.PageArt still = screen.slots(PackArt.ART).isEmpty() ? manifest.page(kit.kit(), PackArt.still(layout)) : null;
    return still != null ? still : manifest.page(kit.kit(), layout);
  }

  /**
   * The dressing host the page art gives for {@code slot}, or null. The one place that maps art slots to host items, so
   * the manifest's host table can change shape here alone.
   */
  public PackManifest.Host host(List<PackManifest.Host> hosts, PackSlot slot) {
    for (PackManifest.Host host : hosts) {
      if (host.slot().equals(slot)) {
        return host;
      }
    }
    return null;
  }

  /** A dressing host: its model, drawn on its slot, with no tooltip. */
  public SlotLook host(PackManifest.Host host) {
    return SlotLook.hidden(host.art().model());
  }

  /**
   * Places {@code markup} where {@code spec} says, wrapped over its lines; in a label of up to two lines, text
   * that needs fewer sits centred in them, a longer block starts at its top. A null spec places nothing.
   */
  public Placement text(TitleComposer title, TextSpec spec, String markup) {
    return text(title, spec, inked(markup), metrics, manifest::row);
  }

  /** {@code markup} as legacy text with its chat colours written as their inks. */
  private static String inked(String markup) {
    return markup == null || markup.isEmpty() ? markup : PackInk.inked(MarkupPort.current().legacy(markup));
  }

  /**
   * A skill, adaptation or mutated skill name where {@code spec} says: on one line or wrapped over its lines in the
   * default font when that fits; else, for a name the condensed font can draw, in the condensed font on one line or
   * wrapped; only then cut short.
   */
  public Placement name(TitleComposer title, TextSpec spec, String markup) {
    if (spec == null || markup == null || markup.isEmpty()) {
      return Placement.EMPTY;
    }
    String plain = LegacyText.plain(MarkupPort.current().legacy(markup));
    PackManifest.Condensed condensed = manifest.condensed();
    if (fits(metrics, spec, plain) || condensed == null || !condensed.metrics().covers(plain)) {
      return text(title, spec, markup);
    }
    return title.measuredIn(condensed.metrics(), () -> text(title, spec, inked(markup), condensed.metrics(), condensed::row));
  }

  /**
   * Whether the names of one grid are drawn in the condensed font: when one of them does not fit its label in the default
   * font and the condensed font can draw every one, so a grid never mixes the two fonts.
   */
  public boolean condensed(List<TextSpec> specs, List<String> markups) {
    PackManifest.Condensed condensed = manifest.condensed();
    if (condensed == null) {
      return false;
    }
    boolean needed = false;
    for (int index = 0; index < specs.size(); index++) {
      TextSpec spec = specs.get(index);
      String markup = markups.get(index);
      if (spec == null || markup == null || markup.isEmpty()) {
        continue;
      }
      String plain = LegacyText.plain(MarkupPort.current().legacy(markup));
      if (!condensed.metrics().covers(plain)) {
        return false;
      }
      needed |= !fits(metrics, spec, plain);
    }
    return needed;
  }

  /** A name of a grid where {@code spec} says, in the condensed font when {@code condensed}, else in the default one; cut only when it does not fit that. */
  public Placement name(TitleComposer title, TextSpec spec, String markup, boolean condensed) {
    if (spec == null || markup == null || markup.isEmpty()) {
      return Placement.EMPTY;
    }
    if (!condensed) {
      return text(title, spec, markup);
    }
    PackManifest.Condensed font = manifest.condensed();
    return title.measuredIn(font.metrics(), () -> text(title, spec, inked(markup), font.metrics(), font::row));
  }

  /** Whether {@code plain} drawn at {@code spec} in the default font fits on its lines without a cut. */
  public boolean fitsDefaultFont(TextSpec spec, String plain) {
    return fits(metrics, spec, plain);
  }

  /** Whether {@code plain} wraps onto the lines of {@code spec} with every line inside its width, in {@code font}. */
  private static boolean fits(PackFontMetrics font, TextSpec spec, String plain) {
    List<String> lines = spec.lines() == 1 ? List.of(plain) : font.wrapWords(plain, spec.width(), spec.breaks());
    if (lines.size() > spec.lines()) {
      return false;
    }
    for (String line : lines) {
      if (!font.fits(line, spec.width())) {
        return false;
      }
    }
    return true;
  }

  private Placement text(TitleComposer title, TextSpec spec, String markup, PackFontMetrics font, IntFunction<RowFont> rowOf) {
    if (spec == null || markup == null || markup.isEmpty()) {
      return Placement.EMPTY;
    }
    if (spec.lines() == 1) {
      return title.text(rowOf.apply(spec.y()), markup, spec.x(), spec.width(), spec.align(), spec.colour());
    }
    int top = firstLine(font, spec, markup);
    int pitch = spec.pitch(font.lineHeight());
    int used = lines(font, spec, markup);
    List<RowFont> rows = new ArrayList<>(used);
    for (int line = 0; line < used; line++) {
      rows.add(rowOf.apply(top + (line * pitch)));
    }
    return title.lines(rows, markup, spec.x(), spec.width(), spec.align(), spec.colour(), spec.breaks());
  }

  /** How many of {@code spec}'s lines {@code markup} takes. */
  public int lines(TextSpec spec, String markup) {
    return lines(metrics, spec, markup);
  }

  private static int lines(PackFontMetrics font, TextSpec spec, String markup) {
    if (spec.lines() == 1) {
      return 1;
    }
    String plain = LegacyText.plain(MarkupPort.current().legacy(markup));
    return Math.max(1, Math.min(spec.lines(), font.wrapWords(plain, spec.width(), spec.breaks()).size()));
  }

  /** The top of {@code markup}'s first line in {@code spec}: centred in a label of up to two lines unless top aligned, else the label's top. */
  private static int firstLine(PackFontMetrics font, TextSpec spec, String markup) {
    if (spec.lines() == 1 || spec.lines() > CENTRED_LINES || spec.top()) {
      return spec.y();
    }
    return spec.y() + (((spec.lines() - lines(font, spec, markup)) * spec.pitch(font.lineHeight())) / 2);
  }

  /** Draws the paint glyph {@code key} relative to {@code x}; false when the pack lacks it. */
  public boolean paint(TitleComposer title, String key, int x) {
    PackManifest.PaintArt art = manifest.paint().get(key);
    if (art == null) {
      return false;
    }
    title.glyphAt(art.font(), art.text(), x + art.dx(), art.advance());
    return true;
  }

  /** Whether the pack has paint glyph {@code key}. */
  public boolean hasPaint(String key) {
    return manifest.paint().containsKey(key);
  }

  /** As {@link #paint(TitleComposer, String, int)}, the white glyph tinted {@code colour}. */
  public void paint(TitleComposer title, String key, int x, int colour) {
    PackManifest.PaintArt art = manifest.paint().get(key);
    if (art != null) {
      title.moveTo(x + art.dx()).glyph(art.font(), art.text(), art.advance(), colour);
    }
  }

  /**
   * The breadcrumb at {@code spec} on {@code kit}'s ribbon (in {@code art}, under every label): parents in paper,
   * separators in the rule colour, the last crumb in gold. Leading crumbs are dropped until the rest fits the label; a
   * last crumb too long on its own is dropped instead, since the page's own heading names it in full.
   */
  public void crumbs(TitleComposer art, TitleComposer title, TextSpec spec, String kit, List<String> crumbs) {
    if (spec == null || crumbs.isEmpty()) {
      return;
    }
    while (crumbs.size() > 1 && !metrics.fits(LegacyText.plain(String.join(CRUMB_SEPARATOR, crumbs)), spec.width())) {
      boolean lastFits = metrics.fits(LegacyText.plain(crumbs.get(crumbs.size() - 1)), spec.width());
      crumbs = lastFits ? crumbs.subList(1, crumbs.size()) : crumbs.subList(0, crumbs.size() - 1);
    }
    StringBuilder markup = new StringBuilder();
    StringBuilder plain = new StringBuilder();
    for (int index = 0; index < crumbs.size(); index++) {
      if (index > 0) {
        markup.append(coloured(CRUMB_RULE, CRUMB_SEPARATOR));
        plain.append(CRUMB_SEPARATOR);
      }
      markup.append(coloured(index == crumbs.size() - 1 ? CRUMB_GILT : CRUMB_PAPER, crumbs.get(index)));
      plain.append(LegacyText.plain(crumbs.get(index)));
    }
    int width = Math.min(spec.width(), metrics.width(plain.toString()));
    ribbon(art, kit, spec.x() - PackArt.RIBBON_LEAD, width + PackArt.RIBBON_PAD);
    text(title, spec, markup.toString(), metrics, manifest::row);
  }

  /**
   * Draws {@code kit}'s crumb ribbon (the tome's when the kit has none) from {@code x}, {@code width} wide: its head,
   * its middle stretched, its tail.
   */
  public void ribbon(TitleComposer title, String kit, int x, int width) {
    String owner = manifest.paint().containsKey(PackArt.ribbonHead(kit)) ? kit : PackArt.TOME;
    int middle = Math.max(0, width - PackArt.RIBBON_HEAD - PackArt.RIBBON_TAIL);
    paint(title, PackArt.ribbonHead(owner), x);
    int at = x + PackArt.RIBBON_HEAD;
    for (int segment : PackArt.segments(middle)) {
      paint(title, PackArt.ribbonBody(owner, segment), at);
      at += segment;
    }
    paint(title, PackArt.ribbonTail(owner), at);
  }

  /** Draws {@code bar} as an outlined track filled {@code fill} (0 to 1) in {@code colour}. */
  public void bar(TitleComposer title, BarSpec bar, double fill, int colour) {
    if (bar == null) {
      return;
    }
    paint(title, PackArt.cap(bar), bar.x() - 1);
    int x = bar.x();
    for (int width : PackArt.segments(bar.width())) {
      paint(title, PackArt.track(width, bar), x);
      x += width;
    }
    paint(title, PackArt.cap(bar), x);
    int filled = (int) Math.round(Math.max(0D, Math.min(1D, fill)) * bar.width());
    segments(title, bar, filled, colour);
  }

  /** Draws {@code rule} across its width in its colour. */
  public void rule(TitleComposer title, BarSpec rule) {
    if (rule != null) {
      segments(title, rule, rule.width(), rule.colour());
    }
  }

  private void segments(TitleComposer title, BarSpec bar, int width, int colour) {
    int x = bar.x();
    for (int segment : PackArt.segments(width)) {
      PackManifest.PaintArt art = manifest.paint().get(PackArt.fill(segment, bar));
      if (art != null) {
        title.moveTo(x + art.dx()).glyph(art.font(), art.text(), art.advance(), colour);
      }
      x += segment;
    }
  }

  /** The item every slot with a pack model is. */
  public Id carrier() {
    return PackManifest.CARRIER;
  }

  /** An item that draws nothing and shows no tooltip. */
  public SlotLook blank() {
    PackManifest.ItemArt art = manifest.item(PackArt.BLANK);
    return SlotLook.hidden(art == null ? null : art.model());
  }

  /** An item that draws nothing but shows its tooltip in the kit's frame, for the cells of a card around its icon. */
  public SlotLook invisible(String kit) {
    PackManifest.ItemArt art = manifest.item(PackArt.BLANK);
    return look(art == null ? null : art.model(), kit);
  }

  /** A plain item with the kit's tooltip frame. */
  public SlotLook framed(String kit) {
    return look(null, kit);
  }

  /**
   * The manifest item {@code key} ({@code ctl/back}, {@code pip/learned_3} ...) in the kit's frame; a framed plain
   * item when the manifest lacks it.
   */
  public SlotLook item(String key, String kit) {
    PackManifest.ItemArt art = manifest.item(key);
    return art == null ? framed(kit) : look(art.model(), kit);
  }

  /** The animated item {@code key} in the kit's frame, marked as that sprite. */
  public SlotLook animated(String key, String kit) {
    return item(key, kit).withSprite(key);
  }

  /** The item drawing sprite {@code spriteId} ({@code dim} picks its greyed copy) in the kit's frame, else a framed plain item. */
  public SlotLook sprite(String spriteId, String kit, boolean dim) {
    PackManifest.ItemArt art = manifest.sprite(spriteId, dim);
    return art == null ? framed(kit) : look(art.model(), kit);
  }

  /**
   * The look of a view icon: a stacked sprite (a mutation's glyph in its parents' tints, an emblem under a trim) on the
   * pack's stack item, which draws each layer named in the custom model data strings in the tint of the same index, when
   * the pack has one; else its sprite drawn by the pack ({@code dim} picks the greyed state, of the bottom layer for a
   * stack), else the item's own model.
   */
  public SlotLook icon(IconRef icon, String kit, boolean dim) {
    List<IconSprites.Layer> layers = icon.spriteId() == null ? List.of() : IconSprites.layers(icon.spriteId());
    PackManifest.ItemArt stack = layers.size() > 1 && !dim ? manifest.item(PackArt.STACK) : null;
    if (stack != null) {
      List<String> sprites = new ArrayList<>(layers.size());
      List<Integer> tints = new ArrayList<>(layers.size());
      for (IconSprites.Layer layer : layers) {
        sprites.add(PackSprites.packId(layer.spriteId()));
        tints.add(layer.tint());
      }
      return look(stack.model(), kit).withModelData(sprites, List.of(), tints);
    }
    PackManifest.ItemArt art = icon.spriteId() == null ? null : manifest.sprite(icon.spriteId(), dim);
    if (art != null) {
      return look(art.model(), kit);
    }
    Id model = icon.itemModelId() == null ? null : Id.tryParse(icon.itemModelId());
    return look(model, kit);
  }

  /** {@code text} in the 0xRRGGBB colour, as legacy hex codes. */
  private static String coloured(int rgb, String text) {
    StringBuilder out = new StringBuilder("§x");
    for (char digit : String.format("%06x", rgb & 0xFFFFFF).toCharArray()) {
      out.append('§').append(digit);
    }
    return out.append(text).toString();
  }

  private SlotLook look(Id model, String kit) {
    PackManifest.TooltipArt tooltip = manifest.tooltip(kit);
    return SlotLook.of(model, tooltip.style(), tooltip.shadow());
  }
}
