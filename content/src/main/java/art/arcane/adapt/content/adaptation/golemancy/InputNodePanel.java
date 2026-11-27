package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.golem.gui.NodePanel;
import art.arcane.adapt.content.golem.gui.NodePanelContext;
import art.arcane.adapt.content.golem.gui.NodePanelControl;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;


final class InputNodePanel implements NodePanel {
  private final GolemSorterCore core;
  private final Function<Id, ItemRef> icons;
  private final BiConsumer<PlayerRef, NodeKey> fetch;

  InputNodePanel(GolemSorterCore core, Function<Id, ItemRef> icons, BiConsumer<PlayerRef, NodeKey> fetch) {
    this.core = Objects.requireNonNull(core);
    this.icons = Objects.requireNonNull(icons);
    this.fetch = Objects.requireNonNull(fetch);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    NodeRecord record = context.record();
    GolemSorterCore.Config config = core.config();
    int level = core.ownerLevel(context.viewer().uuid());
    return List.of(match(record, config, level), radius(record, config, level), fetch(context, config, level));
  }

  private NodePanelControl match(NodeRecord record, GolemSorterCore.Config config, int level) {
    String name = TextFormat.YELLOW + matchName(SorterRules.match(record, config, level)).resolve();
    String lore = TextFormat.GRAY + Text.key("adapt.golemancy.sorter_core.match_lore").resolve();
    ItemRef icon = icons.apply(Id.minecraft("comparator"));
    if (!GolemancyRules.unlocked(level, config.groupsLevel)) {
      return new NodePanelControl("sorter-match", icon, name, List.of(lore, locked(config.groupsLevel)), null, null, null);
    }
    return new NodePanelControl("sorter-match", icon, name, List.of(lore, TextFormat.GRAY + Text.key("adapt.golemancy.gui.cycle").resolve()),
        current -> SorterRules.cycleMatch(current, config, level, true), current -> SorterRules.cycleMatch(current, config, level, false), null);
  }

  private NodePanelControl radius(NodeRecord record, GolemSorterCore.Config config, int level) {
    int levelRadius = SorterRules.destinationRadius(config.destinationRadiusByLevel, level);
    String name = TextFormat.YELLOW + Text.key("adapt.golemancy.sorter_core.radius").withMarkup("radius", String.valueOf(SorterRules.radius(record, levelRadius))).resolve();
    List<String> lore = List.of(TextFormat.GRAY + Text.key("adapt.golemancy.sorter_core.radius_lore").withMarkup("radius", String.valueOf(levelRadius)).resolve(),
        TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.area_lore").resolve());
    return new NodePanelControl("sorter-radius", icons.apply(Id.minecraft("spyglass")), name, lore,
        current -> SorterRules.resize(current, levelRadius, 1), current -> SorterRules.resize(current, levelRadius, -1), null);
  }

  private NodePanelControl fetch(NodePanelContext context, GolemSorterCore.Config config, int level) {
    String name = TextFormat.GREEN + Text.key("adapt.golemancy.sorter_core.fetch").resolve();
    ItemRef icon = icons.apply(ContentIds.itemOr(Id.minecraft("bundle"), Id.minecraft("hopper")));
    if (!GolemancyRules.unlocked(level, config.fetchLevel)) {
      return new NodePanelControl("sorter-fetch", icon, name, List.of(locked(config.fetchLevel)), null, null, null);
    }
    NodeKey key = context.key();
    return new NodePanelControl("sorter-fetch", icon, name, List.of(TextFormat.GRAY + Text.key("adapt.golemancy.sorter_core.fetch_lore").resolve()),
        null, null, viewer -> fetch.accept(viewer, key));
  }

  static String locked(int level) {
    return TextFormat.RED + Text.key("adapt.golemancy.sorter_core.locked").withMarkup("level", String.valueOf(level)).resolve();
  }

  private static Text matchName(SorterRules.Match match) {
    return switch (match) {
      case EXACT -> Text.key("adapt.golemancy.sorter_core.match_exact");
      case GROUPS -> Text.key("adapt.golemancy.sorter_core.match_groups");
      case PRECISE -> Text.key("adapt.golemancy.sorter_core.match_precise");
    };
  }
}
