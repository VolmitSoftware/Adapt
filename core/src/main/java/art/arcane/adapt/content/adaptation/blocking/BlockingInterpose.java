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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class BlockingInterpose extends SimpleAdaptation<BlockingInterpose.Config> {

  public BlockingInterpose() {
    super("blocking-interpose");
    registerConfiguration(Config.class);
    setIcon(BlockingShields.SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_interpose_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_ingot"))
            .key("challenge_blocking_interpose_2k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_interpose_250", "blocking.interpose.damage-redirected", 250, 500);
    registerMilestone("challenge_blocking_interpose_2k", "blocking.interpose.damage-redirected", 2000, 2000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.ALLIES, BlockingPreferences.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getRedirectShare(level), 0), 1);
    statLore(v, Form.f(getRange(level), 1), 2);
    statLore(v, Form.pc(getConfig().lowHealthThreshold, 0), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef ally)) {
      return;
    }

    double maxHealth = ally.maxHealth();
    if (maxHealth <= 0 || ally.health() > maxHealth * getConfig().lowHealthThreshold) {
      return;
    }

    double scanRadius = getConfig().rangeBase + getConfig().rangeFactor;
    Loc allyLocation = ally.location();
    PlayerRef bestBlocker = null;
    int bestLevel = 0;
    double bestDistanceSquared = Double.MAX_VALUE;

    for (BlockingCover.Guard guard : BlockingCover.guards(this, ally, scanRadius, true)) {
      PlayerRef blocker = guard.blocker();
      int level = guard.level();
      int cost = Math.max(1, (int) Math.ceil(e.damage() * getRedirectShare(level) * getConfig().durabilityPerDamage));
      if (!preference(blocker, BlockingPreferences.RESERVE).permits(BlockingShields.preferred(blocker), cost)) { continue; }

      double range = getRange(level);
      double distanceSquared = blocker.location().distanceSquared(allyLocation);
      if (distanceSquared > range * range || distanceSquared >= bestDistanceSquared) {
        continue;
      }

      bestDistanceSquared = distanceSquared;
      bestBlocker = blocker;
      bestLevel = level;
    }

    if (bestBlocker == null) {
      return;
    }

    double before = e.damage();
    double redirected = before * getRedirectShare(bestLevel);
    if (redirected <= 0) {
      return;
    }
    e.setDamage(Math.max(0, before - redirected));

    Loc impact = allyLocation.add(0, 1.0D, 0);
    fx(impact, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.2D)
        .sound(Sounds.ITEM_SHIELD_BLOCK, 0.5F, 1.2F);

    PlayerRef blocker = bestBlocker;
    Tasks.runEntity(blocker, () -> absorbRedirect(blocker, redirected, impact));
  }

  private void absorbRedirect(PlayerRef blocker, double redirected, Loc allyImpact) {
    if (!blocker.isOnline()) {
      return;
    }

    BlockingShields.wear(this, blocker, Math.max(1, (int) Math.ceil(redirected * getConfig().durabilityPerDamage)));
    blocker.setExhaustion(blocker.exhaustion() + (float) getConfig().exhaustionPerRedirect);
    addStat(blocker, "blocking.interpose.damage-redirected", redirected);
    xp(blocker, redirected * getConfig().xpPerDamageRedirected);

    Loc shield = blocker.eyeLocation();
    fx(shield, FxPriority.COMBAT)
        .line(Particles.END_ROD, allyImpact.x(), allyImpact.y(), allyImpact.z(), 6)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 0.8F, 0.9F, Sounds.BLOCK_ANVIL_LAND, 0.3F, 1.3F);
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private double getRedirectShare(int level) {
    return Math.min(getConfig().maxRedirectShare, getConfig().redirectShareBase + (getLevelPercent(level) * getConfig().redirectShareFactor));
  }


  @ConfigDescription("Sneak-block near a wounded ally to redirect part of the damage they take onto your shield.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Share Base for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectShareBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Share Factor for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectShareFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Redirect Share for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRedirectShare = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 4.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Low Health Threshold for the Blocking Interpose adaptation.", impact = "Higher values let you shield allies at higher health fractions.")
    double lowHealthThreshold = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Per Damage for the Blocking Interpose adaptation.", impact = "Higher values wear your shield down faster per point of redirected damage.")
    double durabilityPerDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Exhaustion Per Redirect for the Blocking Interpose adaptation.", impact = "Higher values drain more hunger each time you interpose.")
    double exhaustionPerRedirect = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage Redirected for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamageRedirected = 3.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
