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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class DiscoveryBetterMending extends SimpleAdaptation<DiscoveryBetterMending.Config> {
  public static final PlayerPreference<DiscoveryPreferences.Reserve> XP_RESERVE = DiscoveryPreferences.reserve("xp-reserve", DiscoveryMessages.PREFERENCE_DISCOVERYBETTERMENDING_XP_RESERVE);
  private static final Id MENDING = Id.minecraft("mending");
  public static final PlayerPreference<CommonPreferences.Scale> REPAIR_LIMIT = CommonPreferences.scale("repair-limit", DiscoveryMessages.PREFERENCE_DISCOVERYBETTERMENDING_REPAIR_LIMIT);

  public DiscoveryBetterMending() {
    super("discovery-better-mending");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("phantom_membrane"));
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("anvil"))
        .key("challenge_discovery_mending_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanted_golden_apple"))
            .key("challenge_discovery_mending_100k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_mending_10k", "discovery.better-mending.durability-restored", 10000, 400);
    registerMilestone("challenge_discovery_mending_100k", "discovery.better-mending.durability-restored", 100000, 1500);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, XP_RESERVE, REPAIR_LIMIT);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRepairPerXp(level)), 1);
    statLore(v, getMaxXpSpend(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    InteractAction action = e.action();
    if (action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!canMend(hand) || p.hasCooldown(hand.type())) {
      return;
    }

    if (hand.damage() <= 0) {
      return;
    }

    int availableXp = Math.max(0, experience().totalExperiencePoints(p) - preference(p, XP_RESERVE).amount());
    if (availableXp <= 0) {
      FxPresets.failFizzle(this, p);
      return;
    }

    double repairPerXp = getRepairPerXp(level);
    int maxXpSpend = Math.min(getMaxXpSpend(level), availableXp);
    int currentDamage = hand.damage();
    int maximumDurability = hand.maxDamage();
    int minimumDamage = (int) Math.floor(maximumDurability * (1D - preference(p, REPAIR_LIMIT).multiplier()));
    int repairable = Math.max(0, currentDamage - minimumDamage);
    int xpNeeded = (int) Math.ceil(repairable / repairPerXp);
    int xpSpent = Math.min(maxXpSpend, xpNeeded);
    if (xpSpent <= 0) {
      FxPresets.failFizzle(this, p);
      return;
    }

    int repaired = Math.max(1, (int) Math.round(xpSpent * repairPerXp));
    int newDamage = Math.max(minimumDamage, currentDamage - repaired);

    if (!payExperienceCost(p, "experience", xpSpent, () -> spendExperiencePoints(p, xpSpent))) {
      return;
    }

    hand.setDamage(newDamage);
    p.inventory().setMainHand(hand);
    p.setCooldown(hand.type(), getCooldownTicks(level));
    e.setCancelled(true);

    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16)
        .frame((f, tick, progress) -> {
          f.helix(Particles.WAX_ON, 0.4D, 1.6D, 5, progress * Math.PI * 2.0D);
          if ((tick & 1) == 0) {
            f.particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0.9, 0, 0.2, 0.01);
            f.sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, (float) (1.2D + (progress * 0.4D)));
          }
        })
        .onComplete(() -> {
          if (newDamage <= 0) {
            fx(p.location(), FxPriority.TRANSITION)
                .particle(Particles.TOTEM, 8, 0, 0, 0, 0.15, 0.1)
                .dustRing(new Rgb(0, 255, 255), 0.6D, 12, 1.1F)
                .chord(Sounds.BLOCK_ANVIL_USE, 0.5F, 1.65F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.9F);
          }
        })
        .start();

    xp(p, Math.max(1D, (currentDamage - newDamage) * getConfig().skillXpPerDurability));
    addStat(p, "discovery.better-mending.durability-restored", restoredDurability(currentDamage, currentDamage - newDamage));
  }

  private boolean canMend(ItemRef hand) {
    if (!isItem(hand) || AdaptCore.platform().host().items().maxDurability(hand.type()) <= 0) {
      return false;
    }

    if (hand.enchantmentLevel(MENDING) <= 0) {
      return false;
    }

    return hand.damage() > 0;
  }

  static boolean spendExperiencePoints(PlayerRef player, int amount) {
    if (player == null || amount <= 0) {
      return false;
    }

    ComponentPort experience = experience();
    int available = experience.totalExperiencePoints(player);
    if (available < amount) {
      return false;
    }

    experience.setExperiencePoints(player, available - amount);
    return true;
  }

  private static ComponentPort experience() {
    return AdaptPorts.get(ComponentPort.class, ComponentPort.NONE);
  }

  static int restoredDurability(int currentDamage, int requestedRepair) {
    int normalizedDamage = Math.max(0, currentDamage);
    int newDamage = Math.max(0, normalizedDamage - Math.max(0, requestedRepair));
    return normalizedDamage - newDamage;
  }

  private double getRepairPerXp(int level) {
    return Math.max(0.1, getConfig().repairPerXpBase + (getLevelPercent(level) * getConfig().repairPerXpFactor));
  }

  private int getMaxXpSpend(int level) {
    return Math.max(1, (int) Math.round(getConfig().maxXpSpendBase + (getLevelPercent(level) * getConfig().maxXpSpendFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(6, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksReduction)));
  }

  @ConfigDescription("Sneak-left-click to spend XP and directly mend the Mending item in your hand.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Repair Per Xp Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairPerXpBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Repair Per Xp Factor for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairPerXpFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Xp Spend Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxXpSpendBase = 14.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Xp Spend Factor for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxXpSpendFactor = 130.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 38.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksReduction = 26.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp Per Durability for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpPerDurability = 0.35;

    public Config() {
      costFactor = 0.8;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
