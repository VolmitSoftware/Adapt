package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.golem.gui.NodePanel;
import art.arcane.adapt.content.golem.gui.NodePanelContext;
import art.arcane.adapt.content.golem.gui.NodePanelControl;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;


final class RancherNodePanel implements NodePanel {
  private static final List<Toggle> TOGGLES = List.of(
      new Toggle(RancherRules.Action.BREED, Id.minecraft("wheat"), Text.key("adapt.golemancy.rancher_core.breed_on"), Text.key("adapt.golemancy.rancher_core.breed_off")),
      new Toggle(RancherRules.Action.SHEAR, Id.minecraft("shears"), Text.key("adapt.golemancy.rancher_core.shear_on"), Text.key("adapt.golemancy.rancher_core.shear_off")),
      new Toggle(RancherRules.Action.MILK, Id.minecraft("milk_bucket"), Text.key("adapt.golemancy.rancher_core.milk_on"), Text.key("adapt.golemancy.rancher_core.milk_off")),
      new Toggle(RancherRules.Action.CULL, Id.minecraft("iron_sword"), Text.key("adapt.golemancy.rancher_core.cull_on"), Text.key("adapt.golemancy.rancher_core.cull_off")));

  private final GolemRancherCore core;
  private final Function<Id, ItemRef> icons;

  RancherNodePanel(GolemRancherCore core, Function<Id, ItemRef> icons) {
    this.core = Objects.requireNonNull(core);
    this.icons = Objects.requireNonNull(icons);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    NodeRecord record = context.record();
    GolemRancherCore.Config config = core.config();
    int level = core.ownerLevel(context.viewer().uuid());
    List<NodePanelControl> controls = new ArrayList<>(2 + TOGGLES.size());
    controls.add(species(record, core.species()));
    controls.add(keep(record, config.defaultKeep));
    for (Toggle toggle : TOGGLES) {
      controls.add(toggle(toggle, record, level, unlockLevel(toggle.action(), config)));
    }
    return controls;
  }

  private NodePanelControl species(NodeRecord record, List<Id> tended) {
    Id chosen = RancherRules.species(record).orElse(null);
    String shown = chosen == null
        ? Text.key("adapt.golemancy.rancher_core.species_all").resolve()
        : Format.capitalizeWords(chosen.legacyName().toLowerCase(Locale.ROOT).replace('_', ' '));
    Id egg = chosen == null ? null : spawnEgg(chosen);
    return new NodePanelControl("rancher-species", icons.apply(egg == null ? Id.minecraft("lead") : egg),
        TextFormat.YELLOW + Text.key("adapt.golemancy.rancher_core.species").withMarkup("species", shown).resolve(),
        List.of(TextFormat.GRAY + Text.key("adapt.golemancy.rancher_core.species_lore").resolve()),
        current -> RancherRules.cycleSpecies(current, tended, true),
        current -> RancherRules.cycleSpecies(current, tended, false), null);
  }

  private NodePanelControl keep(NodeRecord record, int fallback) {
    return new NodePanelControl("rancher-keep", icons.apply(Id.minecraft("hay_block")),
        TextFormat.YELLOW + Text.key("adapt.golemancy.rancher_core.keep").withMarkup("count", String.valueOf(RancherRules.keep(record, fallback))).resolve(),
        List.of(TextFormat.GRAY + Text.key("adapt.golemancy.rancher_core.keep_lore").resolve()),
        current -> RancherRules.adjustKeep(current, 1, fallback),
        current -> RancherRules.adjustKeep(current, -1, fallback), null);
  }

  private NodePanelControl toggle(Toggle toggle, NodeRecord record, int level, int unlockLevel) {
    boolean unlocked = GolemancyRules.unlocked(level, unlockLevel);
    boolean on = unlocked && RancherRules.enabled(record, toggle.action());
    String name = on ? TextFormat.GREEN + toggle.onName().resolve() : TextFormat.YELLOW + toggle.offName().resolve();
    List<String> lore = new ArrayList<>(2);
    lore.add(unlocked
        ? TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.toggle").resolve()
        : TextFormat.RED + Text.key("adapt.golemancy.rancher_core.locked").withMarkup("level", String.valueOf(unlockLevel)).resolve());
    if (toggle.action() == RancherRules.Action.CULL) {
      lore.add(TextFormat.DARK_GRAY + Text.key("adapt.golemancy.rancher_core.iron_only").resolve());
    }
    RancherRules.Action action = toggle.action();
    return new NodePanelControl("rancher-" + action.id(), icons.apply(toggle.icon()), name, lore,
        unlocked ? current -> RancherRules.toggle(current, action) : null, null, null);
  }

  private static int unlockLevel(RancherRules.Action action, GolemRancherCore.Config config) {
    return switch (action) {
      case BREED -> 1;
      case SHEAR -> config.shearLevel;
      case MILK -> config.milkLevel;
      case CULL -> config.cullLevel;
    };
  }

  private static Id spawnEgg(Id species) {
    Id egg = species.withPath(species.path() + "_spawn_egg");
    return AdaptApi.platform().host().items().isItem(egg) ? egg : null;
  }

  private record Toggle(RancherRules.Action action, Id icon, Text onName, Text offName) {
  }
}
