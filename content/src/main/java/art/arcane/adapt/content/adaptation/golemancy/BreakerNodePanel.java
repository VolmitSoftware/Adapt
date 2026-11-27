package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.golem.gui.NodePanel;
import art.arcane.adapt.content.golem.gui.NodePanelContext;
import art.arcane.adapt.content.golem.gui.NodePanelControl;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;


final class BreakerNodePanel implements NodePanel {
  private static final Toggle SILK = new Toggle("breaker-silk", Id.minecraft("string"), Text.key("adapt.golemancy.breaker_core.silk_on"),
      Text.key("adapt.golemancy.breaker_core.silk_off"), BreakerRules::toggleSilk);
  private static final Toggle FORTUNE = new Toggle("breaker-fortune", Id.minecraft("diamond"), Text.key("adapt.golemancy.breaker_core.fortune_on"),
      Text.key("adapt.golemancy.breaker_core.fortune_off"), BreakerRules::toggleFortune);

  private final GolemBreakerCore core;
  private final Function<Id, ItemRef> icons;

  BreakerNodePanel(GolemBreakerCore core, Function<Id, ItemRef> icons) {
    this.core = Objects.requireNonNull(core);
    this.icons = Objects.requireNonNull(icons);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    NodeRecord record = context.record();
    GolemBreakerCore.Config config = core.config();
    int level = core.ownerLevel(context.viewer().uuid());
    return List.of(
        filter(record),
        toggle(SILK, BreakerRules.silk(config, level, record), level, config.silkTouchLevel),
        toggle(FORTUNE, BreakerRules.fortune(config, level, record), level, config.fortuneLevel));
  }

  private NodePanelControl filter(NodeRecord record) {
    boolean ready = BreakerRules.filtered(record);
    String name = ready
        ? TextFormat.GREEN + Text.key("adapt.golemancy.breaker_core.filter_ready").resolve()
        : TextFormat.RED + Text.key("adapt.golemancy.breaker_core.filter_needed").resolve();
    return new NodePanelControl("breaker-filter", icons.apply(ready ? Id.minecraft("iron_pickaxe") : Id.minecraft("barrier")), name,
        List.of(TextFormat.GRAY + Text.key("adapt.golemancy.breaker_core.filter_lore").resolve()), null, null, null);
  }

  private NodePanelControl toggle(Toggle toggle, boolean on, int level, int unlockLevel) {
    String name = on ? TextFormat.GREEN + toggle.onName().resolve() : TextFormat.YELLOW + toggle.offName().resolve();
    ItemRef icon = icons.apply(toggle.icon());
    if (!GolemancyRules.unlocked(level, unlockLevel)) {
      return new NodePanelControl(toggle.id(), icon, name,
          List.of(TextFormat.RED + Text.key("adapt.golemancy.breaker_core.locked").withMarkup("level", String.valueOf(unlockLevel)).resolve()), null, null, null);
    }
    return new NodePanelControl(toggle.id(), icon, name,
        List.of(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.toggle").resolve(), TextFormat.DARK_GRAY + Text.key("adapt.golemancy.breaker_core.exclusive").resolve()),
        toggle.flip(), null, null);
  }

  private record Toggle(String id, Id icon, Text onName, Text offName, UnaryOperator<NodeRecord> flip) {
  }
}
