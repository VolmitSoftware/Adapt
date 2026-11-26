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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class HunterBloodTrail extends SimpleAdaptation<HunterBloodTrail.Config> {
  private final HunterWounds wounds = new HunterWounds(this,
      (PlayerRef hunter) -> addStat(hunter, "hunter.blood-trail.trails-followed", 1));

  public HunterBloodTrail() {
    super("hunter-blood-trail");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("redstone"));
    setInterval(250);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("redstone"))
        .key("challenge_hunter_blood_trail_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("redstone_block"))
            .key("challenge_hunter_blood_trail_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_hunter_blood_trail_250", "hunter.blood-trail.trails-followed", 250, 400);
    registerMilestone("challenge_hunter_blood_trail_2500", "hunter.blood-trail.trails-followed", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.TRAIL, HunterPreferences.GLOW);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getTrailDurationTicks(level) / 20.0D, 1), 1);
    statLore(v, C.YELLOW, "+ ", Form.f(getTrackingRange(level), 1), 2);
  }

  static double trackingRange(double levelPercent, double base, double factor) {
    return base + (levelPercent * factor);
  }

  static long trailDurationTicks(double levelPercent, long base, double factor) {
    return base + Math.round(levelPercent * factor);
  }

  long getTrailDurationTicks(int level) {
    return trailDurationTicks(getLevelPercent(level), getConfig().trailDurationTicksBase, getConfig().trailDurationTicksFactor);
  }

  double getTrackingRange(int level) {
    return trackingRange(getLevelPercent(level), getConfig().rangeBase, getConfig().rangeFactor);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null || attack.target() instanceof PlayerRef || !(attack.target() instanceof LivingRef victim)) {
      return;
    }

    double maxHealth = victim.maxHealth();
    double healthAfter = victim.health() - e.finalDamage();
    if (!HunterWounds.isWounded(healthAfter, maxHealth, getConfig().woundHealthFraction)) {
      return;
    }

    PlayerRef p = attack.attacker();
    int level = attack.level();
    if (wounds.mark(p, victim, getTrailDurationTicks(level), getTrackingRange(level), getConfig().maxTrackedWounds)) {
      xpSilent(p, getConfig().xpPerWound);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !wounds.isEmpty();
  }

  @Override
  public void onTick() {
    wounds.tick(getConfig().maxTrackedWounds, new HunterWounds.Style(getConfig().trailThickness, getConfig().displayDurationTicks));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    wounds.forget(event.player().uuid());
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    wounds.forget(player.getPlayer().uuid());
  }

  @Override
  public void unregister() {
    wounds.clear();
    super.unregister();
  }

  @ConfigDescription("Mobs you wound below half health leave a private glowing blood trail only you can see.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base blood-trail duration in ticks for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long trailDurationTicksBase = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional blood-trail duration in ticks gained across levels for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double trailDurationTicksFactor = 200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base tracking range in blocks for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional tracking range in blocks gained across levels for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of max health a target must fall to or below to bleed for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double woundHealthFraction = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum simultaneously tracked blood trails for the Hunter Blood Trail adaptation.", impact = "Lower values reduce scheduling and particle work.")
    int maxTrackedWounds = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Thickness of each private glowing blood trail segment.", impact = "Higher values make the trail wider and easier to follow.")
    double trailThickness = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration of each private glowing blood trail segment, in ticks.", impact = "Higher values leave a longer-lived trail behind wounded targets.")
    int displayDurationTicks = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Silent xp granted when a fresh target starts bleeding for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerWound = 3;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
