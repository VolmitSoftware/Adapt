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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

public class BlockingShieldbearersResolve extends SimpleAdaptation<BlockingShieldbearersResolve.Config> {
  public static final Id ID = Id.adapt("blocking/shieldbearers_resolve");
  private static final Id RESISTANCE = Id.minecraft("resistance");

  public BlockingShieldbearersResolve() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_axe"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_resolve_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_ingot"))
            .key("challenge_blocking_resolve_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_resolve_100", "blocking.shieldbearers-resolve.recoveries", 100, 600);
    registerMilestone("challenge_blocking_resolve_1k", "blocking.shieldbearers-resolve.recoveries", 1000, 2500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.RESISTANCE, BlockingPreferences.RECOVERY);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getRecoverySpeed(level), 0), 1);
    statLore(v, Format.f(getResistanceAmplifier(level) + 1, 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    PlayerRef p = BlockingAxeBlows.defender(this, e);
    if (p == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    long now = System.currentTimeMillis();
    if (getStorageLong(p, "resolveGuardUntil", 0L) > now) {
      return;
    }

    AdaptApi.scheduler().runEntity(p, () -> resolveDisable(p, level), 1);
  }

  private void resolveDisable(PlayerRef p, int level) {
    if (!p.isOnline() || getActiveLevel(p) <= 0
        || (!preferenceEnabled(p, BlockingPreferences.RECOVERY) && !preferenceEnabled(p, BlockingPreferences.RESISTANCE))) {
      return;
    }

    int cooldown = BlockingAxeBlows.disabledTicks(p);
    if (cooldown <= 0) {
      return;
    }

    long now = System.currentTimeMillis();
    if (getStorageLong(p, "resolveGuardUntil", 0L) > now) {
      return;
    }
    setStorage(p, "resolveGuardUntil", now + getConfig().reprocessGuardMillis);

    int reduced = (int) Math.round(cooldown * (1.0D - getRecoverySpeed(level)));
    reduced = Math.max(getConfig().minCooldownTicks, reduced);
    if (reduced < cooldown && preferenceEnabled(p, BlockingPreferences.RECOVERY)) {
      p.setCooldown(BlockingShields.SHIELD, reduced);
    }

    int resistanceTicks = Math.max(getConfig().minResistanceTicks, Math.min(cooldown, reduced));
    if (preferenceEnabled(p, BlockingPreferences.RESISTANCE)) {
      p.addEffect(EffectInstance.of(RESISTANCE, resistanceTicks, getResistanceAmplifier(level)).withParticles(false));
    }

    addStat(p, "blocking.shieldbearers-resolve.recoveries", 1);
    xp(p, getConfig().xpOnResolve);
    fx(p.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .ring(Particles.END_ROD, 0.6D, 8, 0)
        .burst(Particles.CRIT, 8, 0.35D)
        .chord(Sounds.BLOCK_ANVIL_USE, 0.5F, 0.8F, Sounds.ITEM_SHIELD_BLOCK, 0.8F, 1.1F, Sounds.BLOCK_BEACON_ACTIVATE, 0.3F, 1.6F);
  }

  private double getRecoverySpeed(int level) {
    return Math.min(getConfig().maxRecoverySpeed, getConfig().recoverySpeedBase + (getLevelPercent(level) * getConfig().recoverySpeedFactor));
  }

  private int getResistanceAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().resistanceAmplifierBase + (getLevelPercent(level) * getConfig().resistanceAmplifierFactor)));
  }


  @ConfigDescription("When an axe disables your shield, brace with resistance and recover the shield faster.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Recovery Speed Base for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values shave more time off a disabled shield's cooldown.")
    double recoverySpeedBase = 0.2;
    @ConfigDoc(value = "Controls Recovery Speed Factor for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values shave more time off a disabled shield's cooldown as you level up.")
    double recoverySpeedFactor = 0.45;
    @ConfigDoc(value = "Controls Max Recovery Speed for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values allow faster shield recovery at max level.")
    double maxRecoverySpeed = 0.7;
    @ConfigDoc(value = "Controls Resistance Amplifier Base for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values grant a stronger resistance tier.")
    double resistanceAmplifierBase = 0;
    @ConfigDoc(value = "Controls Resistance Amplifier Factor for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values grant a stronger resistance tier as you level up.")
    double resistanceAmplifierFactor = 2.2;
    @ConfigDoc(value = "Controls Min Resistance Ticks for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values guarantee a longer minimum resistance window.")
    int minResistanceTicks = 40;
    @ConfigDoc(value = "Controls Min Cooldown Ticks for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values keep a longer floor on the recovered shield cooldown.")
    int minCooldownTicks = 20;
    @ConfigDoc(value = "Controls Reprocess Guard Millis for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values prevent the same disable from being processed multiple times.")
    long reprocessGuardMillis = 500;
    @ConfigDoc(value = "Controls Xp On Resolve for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnResolve = 12;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
