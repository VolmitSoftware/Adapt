package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.gui.NodePanel;
import art.arcane.adapt.api.golem.gui.NodePanelContext;
import art.arcane.adapt.api.golem.gui.NodePanelControl;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class RancherNodePanel implements NodePanel {
  private static final List<Toggle> TOGGLES = List.of(
      new Toggle(RancherRules.Action.BREED, Material.WHEAT, GolemancyMessages.RANCHER_CORE_BREED_ON, GolemancyMessages.RANCHER_CORE_BREED_OFF),
      new Toggle(RancherRules.Action.SHEAR, Material.SHEARS, GolemancyMessages.RANCHER_CORE_SHEAR_ON, GolemancyMessages.RANCHER_CORE_SHEAR_OFF),
      new Toggle(RancherRules.Action.MILK, Material.MILK_BUCKET, GolemancyMessages.RANCHER_CORE_MILK_ON, GolemancyMessages.RANCHER_CORE_MILK_OFF),
      new Toggle(RancherRules.Action.CULL, Material.IRON_SWORD, GolemancyMessages.RANCHER_CORE_CULL_ON, GolemancyMessages.RANCHER_CORE_CULL_OFF));

  private final GolemRancherCore core;
  private final Function<Material, ItemStack> icons;

  RancherNodePanel(GolemRancherCore core, Function<Material, ItemStack> icons) {
    this.core = Objects.requireNonNull(core);
    this.icons = Objects.requireNonNull(icons);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    NodeRecord record = context.record();
    GolemRancherCore.Config config = core.config();
    int level = core.ownerLevel(context.viewer().getUniqueId());
    List<NodePanelControl> controls = new ArrayList<>(2 + TOGGLES.size());
    controls.add(species(record, core.species()));
    controls.add(keep(record, config.defaultKeep));
    for (Toggle toggle : TOGGLES) {
      controls.add(toggle(toggle, record, level, unlockLevel(toggle.action(), config)));
    }
    return controls;
  }

  private NodePanelControl species(NodeRecord record, List<EntityType> tended) {
    EntityType chosen = RancherRules.species(record).orElse(null);
    String shown = chosen == null
        ? AdaptLanguage.text(GolemancyMessages.RANCHER_CORE_SPECIES_ALL)
        : Form.capitalizeWords(chosen.name().toLowerCase(Locale.ROOT).replace('_', ' '));
    Material egg = chosen == null ? null : Material.matchMaterial(chosen.name() + "_SPAWN_EGG");
    return new NodePanelControl("rancher-species", icons.apply(egg == null ? Material.LEAD : egg),
        C.YELLOW + AdaptLanguage.text(GolemancyMessages.RANCHER_CORE_SPECIES, trusted("species", shown)),
        List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.RANCHER_CORE_SPECIES_LORE)),
        current -> RancherRules.cycleSpecies(current, tended, true),
        current -> RancherRules.cycleSpecies(current, tended, false), null);
  }

  private NodePanelControl keep(NodeRecord record, int fallback) {
    return new NodePanelControl("rancher-keep", icons.apply(Material.HAY_BLOCK),
        C.YELLOW + AdaptLanguage.text(GolemancyMessages.RANCHER_CORE_KEEP, trusted("count", RancherRules.keep(record, fallback))),
        List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.RANCHER_CORE_KEEP_LORE)),
        current -> RancherRules.adjustKeep(current, 1, fallback),
        current -> RancherRules.adjustKeep(current, -1, fallback), null);
  }

  private NodePanelControl toggle(Toggle toggle, NodeRecord record, int level, int unlockLevel) {
    boolean unlocked = RancherRules.unlocked(level, unlockLevel);
    boolean on = unlocked && RancherRules.enabled(record, toggle.action());
    String name = on ? C.GREEN + AdaptLanguage.text(toggle.onName()) : C.YELLOW + AdaptLanguage.text(toggle.offName());
    List<String> lore = new ArrayList<>(2);
    lore.add(unlocked
        ? C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TOGGLE)
        : C.RED + AdaptLanguage.text(GolemancyMessages.RANCHER_CORE_LOCKED, trusted("level", unlockLevel)));
    if (toggle.action() == RancherRules.Action.CULL) {
      lore.add(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.RANCHER_CORE_IRON_ONLY));
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

  private record Toggle(RancherRules.Action action, Material icon, TextKey onName, TextKey offName) {
  }
}
