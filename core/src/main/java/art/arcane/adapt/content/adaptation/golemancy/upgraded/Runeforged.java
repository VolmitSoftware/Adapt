package art.arcane.adapt.content.adaptation.golemancy.upgraded;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.golemancy.AnimusBindingRules;
import art.arcane.adapt.content.adaptation.golemancy.GolemLoans;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runic Augments and Masterwork fused, with twin cores: your golems take runes as Runic Augments allows, Masterwork's
 * infusions and masterworks work at the lent level, every golem gets an extra rune slot and, from {@code tierUpLevel},
 * runes that act one tier higher. Any golem, not just a masterwork, may hold a second core in its menu, up to a count by
 * level; twin cores past the free ones each cost a Masterwork Heart, and the second core works slower. Masterworks get
 * one more rune slot on top.
 */
public class Runeforged extends SkillUpgrade<Runeforged.Config> implements GolemLoans.RunePerks, GolemMastery.Twins {
  public Runeforged() {
    super("upgraded-golemancy-runeforged");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chiseled_stone_bricks"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
    statLore(v, getConfig().extraRuneSlots, 2);
    if (level >= getConfig().tierUpLevel) {
      statLore(v, Form.pc(getConfig().tierThreeBoost, 0), 3);
    }
    int twins = twinsAtLevel(level);
    statLore(v, twins == Integer.MAX_VALUE ? AdaptLanguage.text(GolemancyMessages.RUNEFORGED_ALL) : twins, 4);
    statLore(v, Form.pc(getConfig().secondCoreSpeed, 0), 5);
    statLore(v, getConfig().masterworkRuneSlots, 6);
  }

  @Override
  public void unregister() {
    GolemMastery.unregisterTwins(this);
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    GolemMastery.registerTwins(this);
  }

  @Override
  public GolemMastery.Perks runePerks(UUID owner, GolemHandle handle, boolean masterwork) {
    return perks(ownerLevel(owner, handle), masterwork);
  }

  @Override
  public int maxTwins(UUID owner, GolemHandle handle) {
    return twinsAtLevel(ownerLevel(owner, handle));
  }

  @Override
  public int freeTwins() {
    return Math.max(0, getConfig().freeTwins);
  }

  @Override
  public double secondCoreSpeed() {
    return getConfig().secondCoreSpeed;
  }

  GolemMastery.Perks perks(int level, boolean masterwork) {
    if (level < 1) {
      return GolemMastery.Perks.NONE;
    }
    boolean tierUp = level >= getConfig().tierUpLevel;
    int slots = getConfig().extraRuneSlots + (masterwork ? getConfig().masterworkRuneSlots : 0);
    return new GolemMastery.Perks(slots, false, tierUp, tierUp ? getConfig().tierThreeBoost : 0D);
  }

  int twinsAtLevel(int level) {
    if (level < 1) {
      return 0;
    }
    int twins = AnimusBindingRules.atLevel(getConfig().twinCoresByLevel, level, 0);
    return twins < 0 ? Integer.MAX_VALUE : twins;
  }

  private int ownerLevel(UUID owner, GolemHandle handle) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    if (player == null) {
      return handle == null ? 0 : handle.ownerLevel(getName());
    }
    int level = getLevel(player);
    if (handle != null) {
      handle.recordOwnerLevel(getName(), level);
    }
    return level;
  }

  @ConfigDescription("Every golem gets an extra rune slot and stronger runes, and may run a second core.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Rune slots every golem you own gets on top of its body's and Runic Augments' slots, up to 9.", impact = "Higher values let every golem hold more runes.")
    int extraRuneSlots = 1;
    @ConfigDoc(value = "Rune slots a masterwork golem gets on top of every other slot, up to 9.", impact = "Higher values let masterworks hold more runes.")
    int masterworkRuneSlots = 1;
    @ConfigDoc(value = "Runeforged level at which every golem's runes act one tier higher.", impact = "Lower values unlock stronger runes sooner.")
    int tierUpLevel = 2;
    @ConfigDoc(value = "Fraction added to the numbers of a tier 3 rune once runes act one tier higher. 0.25 is 25% more.", impact = "Higher values make tier 3 runes stronger.")
    double tierThreeBoost = 0.25;
    @ConfigDoc(value = "Golems that may hold a twin core at once, by Runeforged level from level 1 upward; -1 means all. Unloaded and packed golems count.", impact = "Higher values let more golems run two roles.")
    List<Integer> twinCoresByLevel = new ArrayList<>(List.of(2, 4, -1));
    @ConfigDoc(value = "Twin-cored golems you may have before each further twin core also costs a Masterwork Heart.", impact = "Higher values make more twin cores cost only the core.")
    int freeTwins = 2;
    @ConfigDoc(value = "Work speed of a twin golem's second core, above 0 and at most 1. 0.75 is 75% of the first core's speed.", impact = "Higher values make the second role work faster.")
    double secondCoreSpeed = 0.75;
  }
}
