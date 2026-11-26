package art.arcane.adapt.content.adaptation.crafting.upgraded;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.content.adaptation.crafting.PortableStations;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * Portable Tables, A Boutilier's Backpacks and Bulk Artisan fused: open a workstation from your hand, carry whole
 * stacks in a backpack, and craft whole batches at once. It hands out the Artisan's Ledger, whose pages hold recipes
 * crafted straight from the inventory and backpacks, and workstations carried along.
 */
public class FieldWorkshop extends SkillUpgrade<FieldWorkshop.Config> {
  private static final PortableStations.Feedback QUIET = new PortableStations.Feedback() {
    @Override
    public void hungry(Loc handTip) {
    }

    @Override
    public void opened(Loc handTip) {
    }
  };

  private final PortableStations stations = new PortableStations(this);

  public FieldWorkshop() {
    super("upgraded-crafting-field-workshop");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crafting_table"));
    registerTool(new ArtisansLedger(this));
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 2);
    statLore(v, pages(level), 3);
    statLore(v, chainDepth(level), 4);
  }

  int pages(int level) {
    return Math.max(1, getConfig().pagesBase + ((level - 1) * getConfig().pagesPerLevel));
  }

  int chainDepth(int level) {
    return Math.max(0, getConfig().chainDepthBase + ((level - 1) * getConfig().chainDepthPerLevel));
  }

  boolean openStation(PlayerRef p, Id item, CraftingStation station) {
    if (p.hasCooldown(item) || !stations.open(p, item, station, getConfig().cooldownTicks, getConfig().hungerCost, QUIET)) {
      return false;
    }
    addStat(p, "crafting.field-workshop.ledger-stations", 1);
    cue(p, MutationCue.WORK);
    return true;
  }

  void crafted(PlayerRef p, int crafts) {
    addStat(p, "crafting.field-workshop.ledger-crafts", crafts);
    cue(p, MutationCue.WORK);
  }

  @ConfigDescription("Open workstations from your hand, carry whole stacks in backpacks, craft whole batches at once, and keep recipes and workstations in an Artisan's Ledger.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Ticks before the same workstation can be opened from a Ledger page again.", impact = "Lower values let you reopen stations sooner.")
    int cooldownTicks = 60;
    @ConfigDoc(value = "Hunger points taken each time a workstation opens from a Ledger page.", impact = "Higher values make Ledger stations cost more food; 0 makes them free.")
    int hungerCost = 1;
    @ConfigDoc(value = "Ledger pages at level 1.", impact = "Higher values store more recipes and workstations.")
    int pagesBase = 4;
    @ConfigDoc(value = "Ledger pages added per level after the first.", impact = "Higher values reward higher levels with more pages.")
    int pagesPerLevel = 4;
    @ConfigDoc(value = "How many steps of stored parts a Ledger craft makes on the way at level 1.", impact = "Higher values craft deeper chains such as planks to sticks to a tool.")
    int chainDepthBase = 1;
    @ConfigDoc(value = "Chain steps added per level after the first.", impact = "Higher values let higher levels craft longer chains.")
    int chainDepthPerLevel = 1;
  }
}
