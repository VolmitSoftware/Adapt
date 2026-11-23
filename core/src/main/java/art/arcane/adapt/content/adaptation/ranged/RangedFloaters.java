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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptCore;
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
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RangedFloaters extends SimpleAdaptation<RangedFloaters.Config> {
  private static final Id LEVITATION = Id.minecraft("levitation");
  private final DataKey<Integer> shotLevelKey;
  private final DataKey<String> shotOwnerKey;
  private final DataKey<String> shotTargetsKey;

  public RangedFloaters() {
    super("ranged-floaters");
    shotLevelKey = DataKey.adapt("ranged_floaters_level", DataCodecs.INT);
    shotOwnerKey = DataKey.adapt("ranged_floaters_owner", DataCodecs.STRING);
    shotTargetsKey = DataKey.adapt("ranged_floaters_preference", DataCodecs.STRING);
    ProjectileCooperativeKeys.register(shotLevelKey.id(), shotOwnerKey.id(), shotTargetsKey.id());
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shulker_shell"));
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shulker_shell"))
        .key("challenge_ranged_floaters_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_ranged_floaters_200", "ranged.floaters.targets-levitated", 200, 300);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SNEAK, RangedPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getProcChance(level), 0), 1);
    statLore(v, Form.duration(getDurationTicks(level) * 50D, 1), 2);
    statLore(v, (1 + getAmplifier(level)), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef projectile = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || !(projectile.shooter() instanceof PlayerRef player)) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0 || (preferenceEnabled(player, RangedPreferences.SNEAK) && !player.isSneaking())) {
      return;
    }

    DataHolder data = projectile.data();
    data.set(shotLevelKey, level);
    data.set(shotOwnerKey, player.uuid().toString());
    data.set(shotTargetsKey, preference(player, RangedPreferences.TARGETS).name());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.damager())
        || !(e.damager() instanceof ProjectileRef projectile)
        || !(e.entity() instanceof LivingRef target)) {
      return;
    }

    ShotAuthorization authorization = readShotAuthorization(projectile);
    if (authorization == null || isProtectedTarget(authorization.ownerId(), target)) {
      return;
    }

    String selection = projectile.data().get(shotTargetsKey);
    if (selection != null && !RangedPreferences.Targets.valueOf(selection).accepts(target)) {
      return;
    }
    int level = authorization.level();
    if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
      return;
    }

    target.addEffect(new EffectInstance(LEVITATION, getDurationTicks(level), getAmplifier(level), true, true, true));

    int amp = getAmplifier(level);
    fx(target, FxPriority.COMBAT)
        .dustRing(new Rgb(230, 245, 255), 0.7D + (amp * 0.2D), 12, 1.0F)
        .column(Particles.END_ROD, Math.min(16, 10 + (amp * 3)), 2.2D)
        .chord(Sounds.ENTITY_SHULKER_SHOOT, 0.6F, 1.45F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.2F);
    rewardProc(authorization.ownerId());
  }

  private ShotAuthorization readShotAuthorization(ProjectileRef projectile) {
    DataHolder data = projectile.data();
    Integer level = data.get(shotLevelKey);
    String owner = data.get(shotOwnerKey);
    if (level == null || level <= 0 || owner == null) {
      return null;
    }
    try {
      return new ShotAuthorization(UUID.fromString(owner), level);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private boolean isProtectedTarget(UUID ownerId, LivingRef target) {
    if (isProtectedFriendly(null, target)) {
      return true;
    }
    UUID tamer = target.tameOwner();
    return tamer != null && ownerId.equals(tamer);
  }

  private void rewardProc(UUID ownerId) {
    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    if (owner != null) {
      Tasks.runEntity(owner, () -> rewardProcOwned(owner));
    }
  }

  private void rewardProcOwned(PlayerRef owner) {
    if (!owner.isOnline() || !isRuntimeRegistered() || getActiveLevel(owner) <= 0) {
      return;
    }
    addStat(owner, "ranged.floaters.targets-levitated", 1);
    xp(owner, getConfig().skillXpOnProc);
  }

  private double getProcChance(int level) {
    return Math.min(getConfig().maxChance, getConfig().chanceBase + (getLevelPercent(level) * getConfig().chanceFactor));
  }

  private int getDurationTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
  }

  private int getAmplifier(int level) {
    return Math.max(0, (int) Math.floor(getLevelPercent(level) * getConfig().maxAmplifier));
  }

  private record ShotAuthorization(UUID ownerId, int level) {
  }

  @ConfigDescription("Projectiles have a chance to apply levitation to targets.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Chance Base for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chanceBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Chance Factor for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chanceFactor = 0.58;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Chance for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxChance = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksBase = 26.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksFactor = 110.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Amplifier for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAmplifier = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Proc for the Ranged Floaters adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnProc = 8.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 6;
      initialCost = 2;
    }
  }
}
