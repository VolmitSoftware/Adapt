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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.discovery.upgraded.UnityPrime;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;

public class DiscoveryBetterMending extends SimpleAdaptation<DiscoveryBetterMending.Config> {
  public static final Id ID = Id.adapt("discovery/better_mending");
  public static final PlayerPreference<DiscoveryPreferences.Reserve> XP_RESERVE = DiscoveryPreferences.reserve("xp-reserve", Text.key("adapt.discovery.preferences.discoverybettermending.xp-reserve"));
  static final Id MENDING = Id.minecraft("mending");
  public static final PlayerPreference<CommonPreferences.Scale> REPAIR_LIMIT = CommonPreferences.scale("repair-limit", Text.key("adapt.discovery.preferences.discoverybettermending.repair-limit"));

  public DiscoveryBetterMending() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("phantom_membrane"));
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("anvil"))
        .key("challenge_discovery_mending_10k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanted_golden_apple"))
            .key("challenge_discovery_mending_100k")
            .frame(AdvancementFrame.CHALLENGE)
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
    statLore(v, Format.f(getRepairPerXp(level), 1), 1);
    statLore(v, getMaxXpSpend(level), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (!XpMending.isPress(e)) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0 || UnityPrime.covers(this, p)) {
      return;
    }

    XpMending.Press press = XpMending.mend(this, p, rates(level), e);
    if (press.outcome() == XpMending.Outcome.FIZZLED) {
      FxPresets.failFizzle(this, p);
      return;
    }
    if (press.outcome() != XpMending.Outcome.MENDED) {
      return;
    }

    int currentDamage = press.damageBefore();
    int newDamage = press.damageAfter();
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16)
        .frame((f, tick, progress) -> {
          f.helix(Particles.WAX_ON, 0.4D, 1.6D, 5, progress * Math.PI * 2.0D);
          if ((tick & 1) == 0) {
            f.particle(Particles.ENCHANT, 2, 0, 0.9, 0, 0.2, 0.01);
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

  /** The repair rate, XP cap and cooldown of {@code level}. */
  public XpMending.Rates rates(int level) {
    return new XpMending.Rates(getRepairPerXp(level), getMaxXpSpend(level), getCooldownTicks(level));
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
    @ConfigDoc(value = "Controls Repair Per Xp Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairPerXpBase = 2.0;
    @ConfigDoc(value = "Controls Repair Per Xp Factor for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairPerXpFactor = 4.0;
    @ConfigDoc(value = "Controls Max Xp Spend Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxXpSpendBase = 14.0;
    @ConfigDoc(value = "Controls Max Xp Spend Factor for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxXpSpendFactor = 130.0;
    @ConfigDoc(value = "Controls Cooldown Ticks Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 38.0;
    @ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksReduction = 26.0;
    @ConfigDoc(value = "Controls Skill Xp Per Durability for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpPerDurability = 0.35;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 6;
      initialCost = 3;
    }
  }
}
