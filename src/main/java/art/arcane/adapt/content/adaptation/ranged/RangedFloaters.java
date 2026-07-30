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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RangedFloaters extends SimpleAdaptation<RangedFloaters.Config> {
  private final NamespacedKey shotLevelKey;
  private final NamespacedKey shotOwnerKey;

  public RangedFloaters() {
    super("ranged-floaters");
    shotLevelKey = new NamespacedKey(Adapt.instance, "ranged_floaters_level");
    shotOwnerKey = new NamespacedKey(Adapt.instance, "ranged_floaters_owner");
    registerConfiguration(Config.class);
    setIcon(Material.SHULKER_SHELL);
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHULKER_SHELL)
        .key("challenge_ranged_floaters_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_ranged_floaters_200", "ranged.floaters.targets-levitated", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getProcChance(level), 0), 1);
    statLore(v, Form.duration(getDurationTicks(level) * 50D, 1), 2);
    statLore(v, (1 + getAmplifier(level)), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    Projectile projectile = e.getEntity();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || !(projectile.getShooter() instanceof Player player)) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    PersistentDataContainer data = projectile.getPersistentDataContainer();
    data.set(shotLevelKey, PersistentDataType.INTEGER, level);
    data.set(shotOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.getDamager())
        || !(e.getDamager() instanceof Projectile projectile)
        || !(e.getEntity() instanceof LivingEntity target)) {
      return;
    }

    ShotAuthorization authorization = readShotAuthorization(projectile);
    if (authorization == null || isProtectedTarget(authorization.ownerId(), target)) {
      return;
    }

    int level = authorization.level();
    if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
      return;
    }

    target.addPotionEffect(new PotionEffect(
        PotionEffectType.LEVITATION,
        getDurationTicks(level),
        getAmplifier(level),
        true,
        true,
        true
    ), true);

    int amp = getAmplifier(level);
    fx(target, FxPriority.COMBAT)
        .dustRing(Color.fromRGB(230, 245, 255), 0.7D + (amp * 0.2D), 12, 1.0F)
        .column(Particles.END_ROD, Math.min(16, 10 + (amp * 3)), 2.2D)
        .chord(Sound.ENTITY_SHULKER_SHOOT, 0.6F, 1.45F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.2F);
    rewardProc(authorization.ownerId());
  }

  private ShotAuthorization readShotAuthorization(Projectile projectile) {
    PersistentDataContainer data = projectile.getPersistentDataContainer();
    Integer level = data.get(shotLevelKey, PersistentDataType.INTEGER);
    String owner = data.get(shotOwnerKey, PersistentDataType.STRING);
    if (level == null || level <= 0 || owner == null) {
      return null;
    }
    try {
      return new ShotAuthorization(UUID.fromString(owner), level);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private boolean isProtectedTarget(UUID ownerId, LivingEntity target) {
    if (isProtectedFriendly(null, target)) {
      return true;
    }
    if (!(target instanceof Tameable tameable) || !tameable.isTamed()) {
      return false;
    }
    AnimalTamer tamer = tameable.getOwner();
    return tamer != null && ownerId.equals(tamer.getUniqueId());
  }

  private void rewardProc(UUID ownerId) {
    Player owner = Bukkit.getPlayer(ownerId);
    if (owner != null) {
      J.runEntity(owner, () -> rewardProcOwned(owner));
    }
  }

  private void rewardProcOwned(Player owner) {
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
      costFactor = 0.78;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
