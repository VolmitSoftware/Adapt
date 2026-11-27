/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import java.util.function.Supplier;
public class StealthCutpurse extends SimpleAdaptation<StealthCutpurse.Config> {
  public static final Id ID = Id.adapt("stealth/cutpurse");
  public static final PlayerPreference<Targets> TARGET_FILTER = PlayerPreference.of("targets", Targets.class, Targets.ALL, List.of(
          new PlayerPreference.Choice<>(Targets.ALL, Text.key("adapt.stealthcutpurse.preferences.preference_targets_all"), Id.minecraft("gold_ingot"), 1),
          new PlayerPreference.Choice<>(Targets.ILLAGERS, Text.key("adapt.stealthcutpurse.preferences.preference_targets_illagers"), Id.minecraft("crossbow"), 1),
          new PlayerPreference.Choice<>(Targets.PIGLINS, Text.key("adapt.stealthcutpurse.preferences.preference_targets_piglins"), Id.minecraft("golden_sword"), 1))).labelled(Text.key("adapt.stealthcutpurse.preferences.preference_targets"));

  private static final Set<Id> TARGETS = Set.of(
      Id.minecraft("pillager"),
      Id.minecraft("vindicator"),
      Id.minecraft("piglin"),
      Id.minecraft("piglin_brute"));

  private final Supplier<StealthCore> stealth;
  private final StealthPockets pockets = new StealthPockets(this::rewardSteal);

  public StealthCutpurse(Supplier<StealthCore> stealth) {
    super(ID);
    this.stealth = Objects.requireNonNull(stealth);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shears"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("gold_nugget"))
        .key("challenge_stealth_cutpurse_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("emerald"))
            .key("challenge_stealth_cutpurse_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_cutpurse_100", "stealth.get().cutpurse.pockets-picked", 100, 400);
    registerMilestone("challenge_stealth_cutpurse_1k", "stealth.get().cutpurse.pockets-picked", 1000, 1500);
  }

  static double computeStealChance(double base, double factor, double maxChance, double percent) {
    return Math.min(maxChance, base + (percent * factor));
  }

  static int computeLootStacks(double base, double factor, double percent) {
    return Math.max(1, (int) Math.round(base + (percent * factor)));
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TARGET_FILTER);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getStealChance(level), 0), 1);
    statLore(v, Format.f(getLootStacks(level), 0), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    PlayerRef attacker = combat.attacker();
    LivingRef target = combat.target();
    Targets selection = preference(attacker, TARGET_FILTER);
    boolean piglin = target.type().path().startsWith("piglin");
    if ((selection == Targets.PIGLINS && !piglin) || (selection == Targets.ILLAGERS && piglin)) {
      return;
    }
    if (!TARGETS.contains(target.type()) || !(target instanceof MobRef mob)
        || !stealth.get().isUndetected(attacker, target)) {
      return;
    }

    if (StealthPockets.picked(mob)) {
      return;
    }

    int level = combat.level();
    if (ThreadLocalRandom.current().nextDouble() >= getStealChance(level)) {
      return;
    }

    pockets.steal(attacker, mob, (float) getLootQuality(level), getLootStacks(level));
  }

  private void rewardSteal(PlayerRef p, Loc targetLocation) {
    addStat(p, "stealth.get().cutpurse.pockets-picked", 1);
    xp(p, getConfig().xpOnSteal);
    Loc eye = p.eyeLocation();
    fx(targetLocation.add(0, 1.0D, 0), FxPriority.TRAIL)
        .line(Particles.ENCHANT, eye.x(), eye.y(), eye.z(), 5)
        .chord(Sounds.ENTITY_ITEM_PICKUP, 0.6F, 1.5F, Sounds.ENTITY_VILLAGER_NO, 0.3F, 1.2F);
  }

  private double getStealChance(int level) {
    return computeStealChance(getConfig().stealChanceBase, getConfig().stealChanceFactor, getConfig().stealChanceMax, getLevelPercent(level));
  }

  private double getLootQuality(int level) {
    return getConfig().lootQualityBase + (getLevelPercent(level) * getConfig().lootQualityFactor);
  }

  private int getLootStacks(int level) {
    return computeLootStacks(getConfig().lootStacksBase, getConfig().lootStacksFactor, getLevelPercent(level));
  }

  @ConfigDescription("While Stealth reports you undetected, hits on pillagers, vindicators, and piglins can steal loot without a kill.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base chance to pick a pocket on a qualifying sneak-hit.", impact = "Higher values steal more often.")
    double stealChanceBase = 0.25;
    @ConfigDoc(value = "Extra steal chance gained across levels.", impact = "Higher values raise steal chance more per level.")
    double stealChanceFactor = 0.4;
    @ConfigDoc(value = "Maximum steal chance after scaling.", impact = "Caps how reliable pickpocketing becomes.")
    double stealChanceMax = 0.9;
    @ConfigDoc(value = "Base loot luck used when rolling stolen loot.", impact = "Higher values bias steals toward rarer drops.")
    double lootQualityBase = 0.0;
    @ConfigDoc(value = "Extra loot luck gained across levels.", impact = "Higher values improve stolen loot quality more per level.")
    double lootQualityFactor = 2.0;
    @ConfigDoc(value = "Base number of loot stacks taken per successful steal.", impact = "Higher values steal more items at once.")
    double lootStacksBase = 1;
    @ConfigDoc(value = "Extra loot stacks gained across levels.", impact = "Higher values steal more items at higher levels.")
    double lootStacksFactor = 2;
    @ConfigDoc(value = "Experience granted per successful steal.", impact = "Higher values level the adaptation faster.")
    double xpOnSteal = 15;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }

  public enum Targets { ALL, ILLAGERS, PIGLINS }
}
