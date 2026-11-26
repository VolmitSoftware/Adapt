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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AxeGroundSmash extends SimpleAdaptation<AxeGroundSmash.Config> {
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Boolean> armedPlayers = playerState();
  private final AxeSmash smasher = new AxeSmash(this, this::rewardSmash);

  public AxeGroundSmash() {
    super("axe-ground-smash");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_axe"));
    setInterval(4333);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_axe"))
        .key("challenge_axe_ground_smash_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherite_axe"))
        .key("challenge_axe_ground_smash_5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_ground_smash_500", "axe.ground-smash.mobs-hit", 500, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.IGNORE_PASSIVE, AxePreferences.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "+ ", Form.f(getFalloffDamage(f), 1) + " - " + Form.f(getDamage(f), 1), 1);
    statLore(v, C.RED, "+ ", Form.f(getRadius(f), 1), 2);
    statLore(v, C.RED, "+ ", Form.pc(getForce(f), 0), 3);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1), 4);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    if (!e.isSneaking()) {
      armedPlayers.remove(playerId);
      return;
    }

    ItemRef mainHand = p.inventory().mainHand();
    int level = getActiveLevel(p);
    long cooldownMs = getCooldownTime(getLevelPercent(level)) * 50L;
    if (shouldArm(e.isSneaking(), p.isOnGround(), isAxe(mainHand), level,
        cooldowns.isReady(playerId, cooldownMs))) {
      armedPlayers.put(playerId, Boolean.TRUE);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if (e.to() == null) {
      return;
    }

    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    if (!p.isSneaking()) {
      armedPlayers.remove(playerId);
      return;
    }
    if (!p.isOnGround() || armedPlayers.remove(playerId) == null) {
      return;
    }

    ItemRef mainHand = p.inventory().mainHand();
    int level = getActiveLevel(p);
    if (!preference(p, AxePreferences.RESERVE).permits(p.foodLevel()) || !shouldActivate(p.isOnGround(), p.isSneaking(), isAxe(mainHand), level)) {
      return;
    }

    long cooldownMs = getCooldownTime(getLevelPercent(level)) * 50L;
    if (!cooldowns.isReady(playerId, cooldownMs)) {
      return;
    }

    cooldowns.mark(playerId, cooldownMs);
    smash(p, level);
  }

  static boolean shouldArm(boolean sneaking, boolean onGround, boolean holdingAxe,
      int level, boolean cooldownReady) {
    return sneaking && !onGround && holdingAxe && level > 0 && cooldownReady;
  }

  static boolean shouldActivate(boolean onGround, boolean sneaking, boolean holdingAxe,
      int level) {
    return onGround && sneaking && holdingAxe && level > 0;
  }

  private void smash(PlayerRef p, int level) {
    double f = getLevelPercent(level);
    double radius = getRadius(f);
    Loc center = p.location();
    renderSmash(center, radius);
    smasher.smash(p, center, radius, getDamage(f), getFalloffDamage(f), getForce(f),
        getConfig().ignorePassiveMobs || preferenceEnabled(p, AxePreferences.IGNORE_PASSIVE));
  }

  private void rewardSmash(PlayerRef player, List<LivingRef> hits) {
    int totalHits = hits.size();
    addStat(player, "axe.ground-smash.mobs-hit", totalHits);
    if (totalHits >= 5) {
      grantOnce(player, "challenge_axe_ground_smash_5");
    }
  }

  private void renderSmash(Loc center, double radius) {
    int points = (int) Math.min(24.0D, Math.max(8.0D, radius * 2.0D));
    timeline(center)
        .duration(6)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          double r = 0.4D + ((radius - 0.4D) * progress);
          fx.ring(Particles.CRIT_MAGIC, r, points, 0.2D);
          fx.dustRing(r, points, 1.1F);
          if (tick == 0) {
            fx.burst(Particles.SMOKE, 6, 0.35D);
            fx.chord(Sounds.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6F, 0.4F, Sounds.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5F, 0.1F, Sounds.ENTITY_TURTLE_EGG_CRACK, 1.0F, 0.4F);
          }
        })
        .start();
  }

  public int getCooldownTime(double factor) {
    return (int) (((1D - factor) * getConfig().cooldownTicksInverseLevelMultiplier) + getConfig().cooldownTicksBase);
  }

  public double getRadius(double factor) {
    return getConfig().radiusLevelFactorMultiplier * factor;
  }

  public double getDamage(double factor) {
    return getConfig().damageLevelFactorMultiplier * factor;
  }

  public double getForce(double factor) {
    return (getConfig().forceFactorMultiplier * factor) + getConfig().forceBase;
  }

  public double getFalloffDamage(double factor) {
    return getConfig().falloffFactor * factor;
  }

  @ConfigDescription("Jump then crouch to smash all nearby enemies with your axe.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from area damage and its secondary effects.", impact = "When enabled, protected mobs do not consume target limits. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Falloff Factor for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double falloffFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Level Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusLevelFactorMultiplier = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Level Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageLevelFactorMultiplier = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceFactorMultiplier = 1.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Base for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceBase = 0.27;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Inverse Level Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksInverseLevelMultiplier = 225;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
