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
  /** Paper between a header number's mark and the number. */
  static final int STAT_GAP = 1;
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
   * The knowledge {@code knowledge} and the player's power as plain header text, each number right-aligned in its label
   * with its small mark before it.
   */
  void stats(PageBuilder page, long knowledge) {
    stat(page, PackArt.STAT_KNOWLEDGE, PackArt.KNOWLEDGE_SPRITE, String.valueOf(knowledge));
    stat(page, PackArt.STAT_POWER, PackArt.POWER_SPRITE, player.header().powerUsed() + "/" + player.header().powerMax());
  }

  /** The knowledge of {@code skillId} and the player's power in the header. */
  void stats(PageBuilder page, String skillId) {
    SkillLineView line = CatalogIndex.line(player, skillId);
    stats(page, line == null ? 0L : line.knowledge());
  }

  private void stat(PageBuilder page, String role, String sprite, String text) {
    TextSpec spec = page.layout().text(role);
    if (spec == null) {
      return;
    }
    int width = resources.metrics().width(text);
    int x = spec.x() + spec.width() - width;
    page.text(new TextSpec(x, spec.y(), width, Align.RIGHT, spec.colour()), text);
    page.paint(PackArt.statMark(sprite, spec.y()), x - STAT_GAP - PackArt.STAT_MARK);
  }
}
