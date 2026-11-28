package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.layout.BarSpec;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.SlotCycle;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackInk;
import art.arcane.adapt.pack.text.Placement;
import art.arcane.adapt.pack.text.TitleComposer;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuAction;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Assembles one pack page: the page art and paint (faces, bars, rules) under the crumbs and labels on the title,
 * buttons in chest or player slots with their looks (on the carrier item when the pack draws them), groups of slots
 * that share one click and tooltip, the foot bar, the kit's animated dressing hosts in the layout's art slots, and
 * blank stand-ins in every other player slot.
 */
final class PageBuilder {
  static final int ROWS = 6;
  static final String SEPARATOR = " / ";
  /** The first player slot of the hotbar row. */
  static final int HOTBAR = 27;

  private final PackResources resources;
  private final ScreenLayout layout;
  private final String kit;
  private final boolean lower;
  private final TitleComposer art;
  private final TitleComposer title;
  private final PackSurface.Builder surface = PackSurface.builder();
  private final Set<Integer> lowerUsed = new HashSet<>();
  private final List<String> crumbs;
  private final boolean mutated;
  private final List<PackManifest.Host> hosts;
  private final ChestMenu.Builder menu;
  /** The viewer's selected hotbar slot (0 to 8), or -1 when unknown. */
  private final int held;

  PageBuilder(PackResources resources, ScreenLayout layout, PackResources.PageKit kit, List<String> crumbs, boolean mutated, boolean lower,
      int held) {
    this.held = held;
    this.resources = resources;
    this.layout = layout;
    this.kit = kit.kit();
    this.lower = lower;
    this.art = resources.composer();
    this.title = resources.composer();
    this.menu = ChestMenu.builder(LegacyText.plain(crumbs.isEmpty() ? "" : crumbs.get(crumbs.size() - 1)), ROWS);
    this.hosts = resources.page(art, layout, kit);
    this.crumbs = crumbs;
    this.mutated = mutated;
  }

  ScreenLayout layout() {
    return layout;
  }

  PackResources resources() {
    return resources;
  }

  String kit() {
    return kit;
  }

  Placement text(String role, String markup) {
    return text(layout.text(role), markup);
  }

  Placement text(TextSpec spec, String markup) {
    return resources.text(title, spec, markup);
  }

  /** A skill, adaptation or mutated skill name: default font, else the condensed one, cut only as a last resort. */
  Placement name(TextSpec spec, String markup) {
    return resources.name(title, spec, markup);
  }

  /** The names of one grid, all in the default font or all in the condensed one (see {@link PackResources#condensed}). */
  List<Placement> names(List<TextSpec> specs, List<String> markups) {
    boolean condensed = resources.condensed(specs, markups);
    List<Placement> placed = new ArrayList<>(specs.size());
    for (int index = 0; index < specs.size(); index++) {
      placed.add(resources.name(title, specs.get(index), markups.get(index), condensed));
    }
    return placed;
  }

  Placement name(String role, String markup) {
    return name(layout.text(role), markup);
  }

  /** The bar {@code bar} filled {@code fill}, in {@code colour}. */
  void bar(BarSpec bar, double fill, int colour) {
    resources.bar(art, bar, fill, colour);
  }

  /** The rule {@code role} of the layout, starting no further left than {@code from}. */
  void rule(String role, int from) {
    BarSpec rule = layout.bar(role);
    resources.rule(art, rule == null ? null : rule.from(from));
  }

  void rule(BarSpec rule) {
    resources.rule(art, rule);
  }

  /** The paint glyph {@code key} relative to {@code x}, under every label. */
  void paint(String key, int x) {
    resources.paint(art, key, x);
  }

  /** The white paint glyph {@code key} relative to {@code x} tinted {@code colour}, under every label. */
  void paint(String key, int x, int colour) {
    resources.paint(art, key, x, colour);
  }

  /** The kit piece {@code piece} as the face under the run of slots starting at {@code slot}. */
  void face(String piece, PackSlot slot) {
    if (slot != null && (slot.area() == PackSlot.Area.TOP || lower)) {
      paint(PackArt.face(piece, resources.layout().grid().y(slot)), resources.layout().grid().x(slot));
    }
  }

  /**
   * A button over the slots of {@code role}: the kit piece {@code piece} as its face under its label, and clear items
   * sharing one click and tooltip over it, or the armed wash on every slot while it waits for its second click.
   */
  void button(String role, String piece, MenuIcon icon, MenuAction action, boolean armed, Id cooldownGroup) {
    List<PackSlot> slots = layout.slots(role);
    if (slots.isEmpty()) {
      return;
    }
    face(piece, slots.get(0));
    SlotLook look = armed ? resources.animated(PackArt.ARMED, kit) : resources.invisible(kit);
    if (cooldownGroup != null) {
      look = look.withCooldownGroup(cooldownGroup);
    }
    for (int index = 0; index < slots.size(); index++) {
      put(slots.get(index), index == 0 ? icon : icon.withAmount(1), action, look);
    }
  }

  /**
   * One slot. A look with a pack model puts the icon on the carrier item; a null look leaves a real item stack. Art
   * slots never take content.
   */
  void put(PackSlot slot, MenuIcon icon, MenuAction action, SlotLook look) {
    if (slot == null || (slot.area() == PackSlot.Area.LOWER && !lower) || layout.slots(PackArt.ART).contains(slot)) {
      return;
    }
    MenuIcon shown = look != null && look.itemModel() != null
        ? MenuIcon.of(resources.carrier(), icon.name(), icon.lore()).withAmount(icon.amount()).withGlint(icon.glint())
            .withCustomModelData(icon.customModelData()) : icon;
    if (look != null && look.tooltipStyle() != null) {
      shown = inked(shown);
    }
    if (hotbar(slot) && slot.index() - HOTBAR == held) {
      shown = unnamed(shown);
    }
    MenuButton button = new MenuButton(shown, action);
    if (slot.area() == PackSlot.Area.TOP) {
      menu.set(slot.index(), button);
    } else {
      surface.lower(slot.index(), button);
      lowerUsed.add(slot.index());
    }
    if (look != null) {
      surface.look(slot, look);
    }
  }

  /** Slots that act as one control: the first shows {@code lead}, the rest are clear items with the same tooltip. */
  void group(List<PackSlot> slots, MenuIcon icon, MenuAction action, SlotLook lead) {
    for (int index = 0; index < slots.size(); index++) {
      put(slots.get(index), index == 0 ? icon : icon.withAmount(1), action, index == 0 ? lead : resources.invisible(kit));
    }
  }

  void cycle(SlotCycle cycle) {
    if (cycle.slot().area() == PackSlot.Area.TOP || lower) {
      surface.cycle(cycle);
    }
  }

  /**
   * The foot bar: back, the previous and next page arrows with the page count between them when there is more than
   * one page, and the tabs, which take the slots the layout gives them from the right.
   */
  void foot(MenuAction back, int page, int pages, IntConsumer go, List<Tab> tabs) {
    if (back != null) {
      control(layout.slot("foot.back"), named(AdaptLanguage.text(GuiMessages.BACK)), back, PackArt.BACK);
    }
    if (pages > 1) {
      boolean previous = page > 0;
      boolean next = page < pages - 1;
      control(layout.slot("foot.previous"), named(AdaptLanguage.text(GuiMessages.PREVIOUS)),
          previous ? (MenuClick click) -> go.accept(page - 1) : null, previous ? PackArt.PREVIOUS : PackArt.PREVIOUS_OFF);
      control(layout.slot("foot.next"), named(AdaptLanguage.text(GuiMessages.NEXT)),
          next ? (MenuClick click) -> go.accept(page + 1) : null, next ? PackArt.NEXT : PackArt.NEXT_OFF);
      text("foot.count", (page + 1) + SEPARATOR + pages);
    }
    List<PackSlot> seats = layout.slots("foot.tabs");
    int first = seats.size() - tabs.size();
    for (int index = 0; index < tabs.size(); index++) {
      if (first + index < 0) {
        continue;
      }
      Tab tab = tabs.get(index);
      control(seats.get(first + index), named(tab.name()), tab.action(), PackArt.tab(tab.id(), tab.on()));
    }
  }

  /**
   * A control showing the pack item {@code key}. In the hotbar row its face is painted in the title under a blank item
   * instead, since the client's own hotbar mirrors that row of the window.
   */
  void control(PackSlot slot, MenuIcon icon, MenuAction action, String key) {
    control(slot, icon, action, key, resources.item(key, kit));
  }

  /** A control whose face is {@code face} (an item key or a sprite id), drawn by {@code look} outside the hotbar row. */
  void control(PackSlot slot, MenuIcon icon, MenuAction action, String face, SlotLook look) {
    if (slot != null && hotbar(slot) && lower
        && resources.paint(art, PackArt.controlFace(face, resources.layout().grid().y(slot)), resources.layout().grid().x(slot))) {
      put(slot, icon, action, resources.invisible(kit));
      return;
    }
    put(slot, icon, action, look);
  }

  /** True for a slot of the window's hotbar row. */
  static boolean hotbar(PackSlot slot) {
    return slot.area() == PackSlot.Area.LOWER && slot.index() >= HOTBAR;
  }

  /** The page with the kit's dressing hosts in the layout's art slots, which hold nothing else. */
  PackPage build() {
    TextSpec crumbSpec = layout.ribbon().shows(mutated) ? layout.text("crumbs") : null;
    resources.crumbs(art, title, crumbSpec, kit, crumbs);
    for (PackSlot slot : layout.slots(PackArt.ART)) {
      PackManifest.Host host = resources.host(hosts, slot);
      if (host == null || (slot.area() == PackSlot.Area.LOWER && !lower)) {
        continue;
      }
      MenuButton inert = MenuButton.display(MenuIcon.of(resources.carrier(), "", List.of()));
      if (slot.area() == PackSlot.Area.TOP) {
        menu.set(slot.index(), inert);
      } else {
        surface.lower(slot.index(), inert);
        lowerUsed.add(slot.index());
      }
      surface.look(slot, resources.host(host));
    }
    if (lower) {
      for (int index = 0; index < PackSlot.LOWER_SLOTS; index++) {
        if (lowerUsed.add(index)) {
          surface.lower(index, MenuButton.display(MenuIcon.of(resources.carrier(), "", List.of())));
          surface.look(PackSlot.lower(index), resources.blank());
        }
      }
    }
    art.moveTo(resources.manifest().titleX());
    List<TitleRun> runs = new ArrayList<>(art.runs());
    runs.addAll(title.runs());
    return new PackPage(menu.build(), surface.title(runs).topRowsOnly(!lower).build());
  }

  /**
   * {@code icon} with no display name and its name as the first tooltip line. The client prints the name of the item in
   * the player's selected hotbar slot above the hotbar for a while each time that item changes (its hover name, the
   * same component the tooltip's title line shows), and an empty name prints nothing; so only the stand-in in the
   * selected slot goes unnamed and the other eight keep their names as titles.
   */
  static MenuIcon unnamed(MenuIcon icon) {
    if (icon.name() == null || icon.name().isEmpty()) {
      return icon;
    }
    List<String> lore = new ArrayList<>();
    lore.add(icon.name());
    lore.addAll(icon.lore());
    return relabelled(icon, "", lore);
  }

  /** {@code icon} with the chat colours of its name and lore written as inks, for a tooltip on a parchment frame. */
  static MenuIcon inked(MenuIcon icon) {
    List<String> lore = new ArrayList<>(icon.lore().size());
    for (String line : icon.lore()) {
      lore.add(PackInk.inked(MarkupPort.current().legacy(line)));
    }
    String name = icon.name() == null ? null : PackInk.inked(MarkupPort.current().legacy(icon.name()));
    return relabelled(icon, name, lore);
  }

  private static MenuIcon relabelled(MenuIcon icon, String name, List<String> lore) {
    return MenuIcon.of(icon.type(), name, lore).withAmount(icon.amount()).withGlint(icon.glint())
        .withItemModel(icon.itemModel()).withCustomModelData(icon.customModelData()).withItem(icon.item());
  }

  static MenuIcon named(String name) {
    return MenuIcon.of(PackManifest.CARRIER, name, List.of());
  }

  static MenuIcon named(String name, List<String> lore) {
    return MenuIcon.of(PackManifest.CARRIER, name, lore);
  }

  /**
   * The menu icon of a view icon: its item, custom model data and item model, named, with lore, one of it; levels are
   * written on the page, never drawn as a stack count.
   */
  static MenuIcon icon(IconRef icon, String name, List<String> lore) {
    return stack(icon, name, lore, 1);
  }

  /** A real item stack of {@code count}, as a recipe shows its ingredients and result. */
  static MenuIcon stack(IconRef icon, String name, List<String> lore, int count) {
    Id type = Id.tryParse(icon.itemId());
    Id model = icon.itemModelId() == null ? null : Id.tryParse(icon.itemModelId());
    return MenuIcon.of(type == null ? PackManifest.CARRIER : type, name, lore).withAmount(Math.max(1, Math.min(99, count)))
        .withItemModel(model).withCustomModelData(icon.customModelData());
  }

  /** A foot tab: its id names the {@link PackArt#tab} item. */
  record Tab(String id, String name, boolean on, MenuAction action) {
  }
}
