package art.arcane.adapt.pack.screen;

import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.text.Align;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;

/** Everything one render of a pack screen reads: the viewer, their screen state, the pack and the view models. */
final class PackFrame {
  /** Paper between a heading and the rule running on from it. */
  static final int RULE_GAP = 4;
  /** Paper between the two header chips, and between them and the crumb ribbon. */
  static final int CHIP_GAP = 3;
  /** A chip's mark and the paper after it, before the number. */
  static final int CHIP_MARK = 19;
  static final int CHIP_MIN = 26;
  private final PackScreens screens;
  private final PlayerRef viewer;
  private final PackViewer state;
  private final PackResources resources;
  private final boolean lower;
  private final CatalogIndex catalog;
  private final PlayerView player;

  PackFrame(PackScreens screens, PlayerRef viewer, PackViewer state, PackResources resources, boolean lower,
            CatalogIndex catalog, PlayerView player) {
    this.screens = screens;
    this.viewer = viewer;
    this.state = state;
    this.resources = resources;
    this.lower = lower;
    this.catalog = catalog;
    this.player = player;
  }

  PackScreens screens() {
    return screens;
  }

  PlayerRef viewer() {
    return viewer;
  }

  PackViewer state() {
    return state;
  }

  CatalogIndex catalog() {
    return catalog;
  }

  PlayerView player() {
    return player;
  }

  /** True when the page lays out over the player slots too. */
  boolean lower() {
    return lower;
  }

  ScreenLayout layout(String screen) {
    return resources.layout().screen(screen, lower);
  }

  /**
   * Leaf {@code leaf} of a page of {@code screen} dressed for {@code skill} (a kit id, or a skill whose mutation picks
   * the lead's or the partner's kit by leaf) under {@code crumbs}, outermost first.
   */
  PageBuilder page(String screen, String skill, int leaf, List<String> crumbs) {
    boolean mutated = PairKeys.parse(skill) != null;
    return new PageBuilder(resources, layout(screen), PackResources.kit(skill, leaf), crumbs, mutated, lower, held());
  }

  /** The viewer's selected hotbar slot, or -1 when it cannot be read. */
  private int held() {
    PlayerInventoryRef inventory = viewer.inventory();
    return inventory == null ? -1 : inventory.heldSlot();
  }

  /**
   * The knowledge of {@code skillId} and the player's power in the header, each on a chip sized to its number with its
   * mark at the chip's left, the power chip ending at its label's right edge and the knowledge chip before it; the crumb
   * ribbon keeps left of them.
   */
  void chips(PageBuilder page, String skillId) {
    SkillLineView line = CatalogIndex.line(player, skillId);
    int right = chip(page, "chip.power", PackArt.HEADER_POWER_MARK, player.header().powerUsed() + "/" + player.header().powerMax(),
        Integer.MAX_VALUE);
    int left = chip(page, "chip.knowledge", PackArt.HEADER_KNOWLEDGE_MARK, String.valueOf(line == null ? 0L : line.knowledge()),
        right - CHIP_GAP);
    page.crumbsBefore(left - CHIP_GAP);
  }

  /** One header chip whose right edge is the label's ({@code right} when smaller); returns its left edge. */
  private int chip(PageBuilder page, String role, String mark, String text, int right) {
    TextSpec spec = page.layout().text(role);
    if (spec == null) {
      return right;
    }
    int end = Math.min(right, spec.x() + spec.width() + PackArt.CHIP_END);
    int textWidth = resources.metrics().width(text);
    int width = Math.max(CHIP_MIN, CHIP_MARK + textWidth + PackArt.CHIP_END);
    int left = end - width;
    page.paint(PackArt.CHIP_HEAD, left);
    int at = left + PackArt.CHIP_END;
    for (int segment : PackArt.segments(width - (PackArt.CHIP_END * 2))) {
      page.paint(PackArt.chipBody(segment), at);
      at += segment;
    }
    page.paint(PackArt.CHIP_TAIL, at);
    page.paint(mark, left + PackArt.CHIP_MARK_DX);
    page.text(new TextSpec(end - PackArt.CHIP_END - textWidth, spec.y(), textWidth, Align.RIGHT, spec.colour()), text);
    return left;
  }
}
