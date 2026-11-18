package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.gui.NodePanel;
import art.arcane.adapt.api.golem.gui.NodePanelContext;
import art.arcane.adapt.api.golem.gui.NodePanelControl;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

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
    String name = C.YELLOW + AdaptLanguage.text(matchName(SorterRules.match(record, config, level)));
    String lore = C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_MATCH_LORE);
    ItemRef icon = icons.apply(Id.minecraft("comparator"));
    if (!GolemancyRules.unlocked(level, config.groupsLevel)) {
      return new NodePanelControl("sorter-match", icon, name, List.of(lore, locked(config.groupsLevel)), null, null, null);
    }
    return new NodePanelControl("sorter-match", icon, name, List.of(lore, C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE)),
        current -> SorterRules.cycleMatch(current, config, level, true), current -> SorterRules.cycleMatch(current, config, level, false), null);
  }

  private NodePanelControl radius(NodeRecord record, GolemSorterCore.Config config, int level) {
    int levelRadius = SorterRules.destinationRadius(config.destinationRadiusByLevel, level);
    String name = C.YELLOW + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_RADIUS, trusted("radius", SorterRules.radius(record, levelRadius)));
    List<String> lore = List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_RADIUS_LORE, trusted("radius", levelRadius)),
        C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_AREA_LORE));
    return new NodePanelControl("sorter-radius", icons.apply(Id.minecraft("spyglass")), name, lore,
        current -> SorterRules.resize(current, levelRadius, 1), current -> SorterRules.resize(current, levelRadius, -1), null);
  }

  private NodePanelControl fetch(NodePanelContext context, GolemSorterCore.Config config, int level) {
    String name = C.GREEN + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH);
    ItemRef icon = icons.apply(Id.minecraft("bundle"));
    if (!GolemancyRules.unlocked(level, config.fetchLevel)) {
      return new NodePanelControl("sorter-fetch", icon, name, List.of(locked(config.fetchLevel)), null, null, null);
    }
    NodeKey key = context.key();
    return new NodePanelControl("sorter-fetch", icon, name, List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_LORE)),
        null, null, viewer -> fetch.accept(viewer, key));
  }

  static String locked(int level) {
    return C.RED + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_LOCKED, trusted("level", level));
  }

  private static TextKey matchName(SorterRules.Match match) {
    return switch (match) {
      case EXACT -> GolemancyMessages.SORTER_CORE_MATCH_EXACT;
      case GROUPS -> GolemancyMessages.SORTER_CORE_MATCH_GROUPS;
      case PRECISE -> GolemancyMessages.SORTER_CORE_MATCH_PRECISE;
    };
  }
}
